/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.util

import groovy.transform.CompileStatic
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.config.PixiOpts
import io.seqera.wave.exception.BadRequestException

import static TemplateUtils.condaFileToDockerFileUsingPixi
import static TemplateUtils.condaFileToDockerFileUsingPixiV1Fast
import static TemplateUtils.condaFileToSingularityFileUsingPixi
import static TemplateUtils.pixiLockFileToDockerFile
import static TemplateUtils.pixiLockFileToSingularityFile
import static TemplateUtils.pixiLockUrlToDockerFile
import static TemplateUtils.pixiLockUrlToSingularityFile

/**
 * Helper class for Pixi-based container builds, i.e. the v1 (PIXI_V1) template, the
 * layered v1-fast (PIXI_V1_FAST) template and the lock file based v1-lock (PIXI_V1_LOCK) template.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class PixiHelper {

    /**
     * Generate a container file (Dockerfile or Singularity) using the Pixi template.
     * Only supports CONDA package type. Lock files are not supported.
     *
     * @param spec The packages specification (must be CONDA type)
     * @param containerImage Optional base container image override
     * @param singularity When true, generates Singularity format; otherwise Dockerfile
     * @return The generated container file content
     * @throws BadRequestException if lock file is detected or package type is not CONDA
     */
    static String containerFile(PackagesSpec spec, String containerImage, boolean singularity) {
        final opts = pixiOpts(spec, containerImage, 'conda/pixi:v1')
        return singularity
                ? condaFileToSingularityFileUsingPixi(opts)
                : condaFileToDockerFileUsingPixi(opts)
    }

    /**
     * Generate a Dockerfile using the `conda/pixi:v1-fast` template. The Conda environment is installed
     * as with the v1 template and then split into multiple image layers by the {@code condasplit} tool.
     * Only supports CONDA package type and Docker format. Lock files are not supported.
     *
     * @param spec The packages specification (must be CONDA type)
     * @param containerImage Optional base container image override
     * @param layersImage The image providing the {@code condasplit} tool
     * @return The generated Dockerfile content
     * @throws BadRequestException if lock file is detected or package type is not CONDA
     */
    static String containerFileV1Fast(PackagesSpec spec, String containerImage, String layersImage) {
        final opts = pixiOpts(spec, containerImage, 'conda/pixi:v1-fast')
        return condaFileToDockerFileUsingPixiV1Fast(opts, layersImage)
    }

    /**
     * Generate a container file (Dockerfile or Singularity) using the `conda/pixi:v1-lock` template.
     * The environment is installed from a Pixi lock file with {@code pixi install --frozen}, i.e. without
     * re-solving it. The lock file is either the build context {@code conda.yml} file or a remote URL.
     *
     * @param spec The packages specification (must be CONDA type)
     * @param containerImage Optional base container image override
     * @param singularity When true, generates Singularity format; otherwise Dockerfile
     * @return The generated container file content
     * @throws BadRequestException if package type is not CONDA or no lock file is provided
     */
    static String containerFileV1Lock(PackagesSpec spec, String containerImage, boolean singularity) {
        if( spec.type != PackagesSpec.Type.CONDA ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by 'conda/pixi:v1-lock' build template")
        }

        final lockFileUrl = CondaHelper.tryGetLockFile(spec.entries)
        if( !lockFileUrl && !spec.environment ) {
            throw new BadRequestException("Build template 'conda/pixi:v1-lock' requires a Pixi lock file")
        }
        final opts = spec.pixiOpts ?: new PixiOpts()
        // the v1 default image can't read lock files created by recent pixi versions
        if( opts.pixiImage == PixiOpts.DEFAULT_PIXI_IMAGE )
            opts.pixiImage = PixiOpts.DEFAULT_PIXI_LOCK_IMAGE
        if( containerImage )
            opts.baseImage = containerImage

        if( lockFileUrl ) {
            return singularity
                    ? pixiLockUrlToSingularityFile(lockFileUrl, opts)
                    : pixiLockUrlToDockerFile(lockFileUrl, opts)
        }
        return singularity
                ? pixiLockFileToSingularityFile(opts)
                : pixiLockFileToDockerFile(opts)
    }

    /**
     * The Pixi options of the pixi templates, i.e. v1 and v1-fast
     *
     * @throws BadRequestException if lock file is detected or package type is not CONDA
     */
    static private PixiOpts pixiOpts(PackagesSpec spec, String containerImage, String template) {
        if( spec.type != PackagesSpec.Type.CONDA ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by '${template}' build template")
        }

        final lockFile = CondaHelper.tryGetLockFile(spec.entries)
        if( lockFile ) {
            throw new BadRequestException("Conda lock file is not supported by '${template}' template")
        }

        final opts = spec.pixiOpts ?: new PixiOpts()
        if( containerImage )
            opts.baseImage = containerImage
        return opts
    }
}
