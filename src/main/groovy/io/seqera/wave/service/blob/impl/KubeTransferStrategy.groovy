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

import com.google.common.hash.Hashing
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.TransferStrategy
import io.seqera.wave.service.blob.TransferTimeoutException
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
/**
 * Implements {@link TransferStrategy} that runs s5cmd using a
 * Kubernetes job
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.build.k8s')
@Replaces(SimpleTransferStrategy)
class KubeTransferStrategy implements TransferStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Inject
    private K8sService k8sService

    @Override
    BlobCacheInfo transfer(BlobCacheInfo info, List<String> command) {
        final name = getName(info)
        final job = k8sService.transferJob(name, blobConfig.s5Image, command, blobConfig)
        final podList = k8sService.waitJob(job, blobConfig.transferTimeout.toMillis())
        if ( !podList || podList.items.size() < 1 ) {
            throw new TransferTimeoutException("Blob transfer job timeout")
        }
        final podName = podList.items[0].metadata.name
        final pod = k8sService.getPod(podName)
        final terminated = k8sService.waitPod(pod, blobConfig.transferTimeout.toMillis())
        final stdout = k8sService.logsPod(podName, name)
        return terminated
                ? info.completed(terminated.exitCode, stdout)
                : info.failed(stdout)
    }

    protected static String getName(BlobCacheInfo info) {
        return "transfer-" + Hashing
                .sipHash24()
                .newHasher()
                .putUnencodedChars(info.locationUri)
                .putUnencodedChars(info.creationTime.toString())
                .hash()
    }
}
