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

package io.seqera.wave.service.blob.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.TransferStrategy
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import static io.seqera.wave.util.K8sHelper.getNoArchSelector
/**
 * Implements {@link TransferStrategy} that runs s5cmd using a
 * Kubernetes job
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.build.k8s')
@Requires(property = 'wave.blobCache.enabled', value = 'true')
class KubeTransferStrategy implements TransferStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Inject
    private K8sService k8sService

    @Property(name='wave.build.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    @Override
    void launchJob(String jobName, List<String> command) {
        final selector = getNoArchSelector(nodeSelectorMap)
        // run the transfer job
        k8sService.launchTransferJob(jobName, blobConfig.s5Image, command, blobConfig, selector)
    }

}
