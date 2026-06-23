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

import static TemplateUtils.pixiTomlFileToDockerFile
import static TemplateUtils.pixiTomlFileToSingularityFile
import static TemplateUtils.pixiTomlUrlToDockerFile
import static TemplateUtils.pixiTomlUrlToSingularityFile

/**
 * Helper class for Pixi manifest (pixi.toml) file-based container builds (CONDA_PIXI_TOML_V1 template).
 * Unlike the lock-file path, this runs {@code pixi install} with online solving, making it
 * equivalent to providing a conda.yml but using the Pixi toolchain.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class PixiTomlHelper {

    static String containerFile(PackagesSpec spec, String containerImage, boolean singularity) {
        if( spec.type != PackagesSpec.Type.CONDA ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by 'conda/pixi-toml:v1' build template")
        }

        final tomlFileUrl = CondaHelper.tryGetLockFile(spec.entries)
        final opts = spec.pixiOpts ?: new PixiOpts()
        if( containerImage )
            opts.baseImage = containerImage

        if( tomlFileUrl ) {
            return singularity
                    ? pixiTomlUrlToSingularityFile(tomlFileUrl, opts)
                    : pixiTomlUrlToDockerFile(tomlFileUrl, opts)
        }

        return singularity
                ? pixiTomlFileToSingularityFile(opts)
                : pixiTomlFileToDockerFile(opts)
    }
}
