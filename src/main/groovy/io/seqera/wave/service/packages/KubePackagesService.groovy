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

package io.seqera.wave.service.packages

import java.nio.file.Path
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.PackagesConfig
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements K8s based AbstractPackagesService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(property = 'wave.build.k8s')
@CompileStatic
class KubePackagesService extends AbstractPackagesService {

    @Inject
    private PackagesConfig config

    @Inject
    private K8sService k8sService

    @Override
    protected void run(List<String> command, Path workDir) {
        String podName = "conda-fetcher-${Instant.now().toEpochMilli()}"
        log.info("Conda fetcher command: $command")
        final pod = k8sService.packagesFetcherContainer(podName, config.condaImage, command, workDir, config)
        final terminated = k8sService.waitPod(pod, config.timeout.toMillis())
        if( terminated ) {
            log.info("Conda packages fetched successfully")
        }
        else{
            final logs = k8sService.logsPod(podName)
            throw new IllegalStateException("Conda fetcher failed - logs: $logs")
        }
    }
}
