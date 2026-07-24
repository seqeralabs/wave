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

import static TemplateUtils.pixiLockFileToDockerFile
import static TemplateUtils.pixiLockFileToSingularityFile
import static TemplateUtils.pixiLockUrlToDockerFile
import static TemplateUtils.pixiLockUrlToSingularityFile

/**
 * Helper class for Pixi lock file-based container builds (CONDA_PIXI_LOCK_V1 template).
 *
 * @author Julianus Pfeuffer <8102638+jpfeuffer@users.noreply.github.com>
 */
@CompileStatic
class PixiLockHelper {

    static String containerFile(PackagesSpec spec, String containerImage, boolean singularity) {
        if( spec.type != PackagesSpec.Type.CONDA ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by 'conda/pixi-lock:v1' build template")
        }

        final lockFileUrl = CondaHelper.tryGetLockFile(spec.entries)
        final opts = spec.pixiOpts ?: new PixiOpts()
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
}
