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

import static io.seqera.wave.util.DockerHelper.condaFileToDockerFileUsingPixi
import static io.seqera.wave.util.DockerHelper.condaFileToSingularityFileUsingPixi

/**
 * Helper class for Pixi-based container builds (PIXI_V1 template).
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
        if( spec.type != PackagesSpec.Type.CONDA ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by 'pixi/v1' build template")
        }

        final lockFile = CondaHelper.tryGetLockFile(spec.entries)
        if( lockFile ) {
            throw new BadRequestException("Conda lock file is not supported by 'pixi/v1' template")
        }

        final opts = spec.pixiOpts ?: new PixiOpts()
        if( containerImage )
            opts.baseImage = containerImage

        return singularity
                ? condaFileToSingularityFileUsingPixi(opts)
                : condaFileToDockerFileUsingPixi(opts)
    }
}
