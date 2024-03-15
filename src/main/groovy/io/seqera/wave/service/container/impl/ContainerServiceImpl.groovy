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

package io.seqera.wave.service.container.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.container.ContainerService
import jakarta.inject.Singleton
import static io.seqera.wave.util.CondaHelper.condaLock
import static io.seqera.wave.util.DockerHelper.condaFileToDockerFile
import static io.seqera.wave.util.DockerHelper.condaFileToSingularityFile
import static io.seqera.wave.util.DockerHelper.condaPackagesToDockerFile
import static io.seqera.wave.util.DockerHelper.condaPackagesToSingularityFile
import static io.seqera.wave.util.DockerHelper.spackFileToDockerFile
import static io.seqera.wave.util.DockerHelper.spackFileToSingularityFile
/**
 * Implements container service
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerServiceImpl implements ContainerService {

    @Override
    String createContainerFile(SubmitContainerTokenRequest request) {
        def packages = request.packages
        final String lock = condaLock(packages.packages)
        if(packages.type == PackagesSpec.Type.CONDA) {
            def result
            if (lock != null || lock.size() > 0) {
                result = request.formatSingularity()
                        ? condaPackagesToSingularityFile(lock, packages.channels, packages.condaOpts)
                        : condaPackagesToDockerFile(lock, packages.channels, packages.condaOpts)
            } else {
                result = request.formatSingularity()
                        ? condaFileToSingularityFile(packages.condaOpts)
                        : condaFileToDockerFile(packages.condaOpts)
            }
            return result
        }

        if( packages.type == PackagesSpec.Type.SPACK ) {
            final result = request.formatSingularity()
                    ? spackFileToSingularityFile(packages.spackOpts)
                    : spackFileToDockerFile(packages.spackOpts)
            return result
        }

    return null
    }

}
