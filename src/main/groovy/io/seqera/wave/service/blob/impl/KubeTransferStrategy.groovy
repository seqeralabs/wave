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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import com.google.common.hash.Hashing
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.TransferStrategy
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import jakarta.inject.Named

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

    @Inject
    private CleanupStrategy cleanup

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Override
    BlobCacheInfo transfer(BlobCacheInfo info, List<String> command) {
        final podName = podName(info)
        final pod = k8sService.transferContainer(podName, blobConfig.s5Image, command, blobConfig)
        final terminated = k8sService.waitPod(pod, blobConfig.transferTimeout.toMillis())
        final stdout = k8sService.logsPod(podName)
        final result =  terminated
                ? info.completed(terminated.exitCode, stdout)
                : info.failed(stdout)
        if( cleanup.shouldCleanup(terminated.exitCode) ) {
            CompletableFuture.supplyAsync (() -> cleanup(podName), executor)
        }
        return result
    }

    protected String podName(BlobCacheInfo info) {
        return 'transfer-' + Hashing
                .sipHash24()
                .newHasher()
                .putUnencodedChars(info.locationUri)
                .putUnencodedChars(info.creationTime.toString())
                .hash()
    }

    void cleanup(String podName) {
        try {
            def pod = k8sService.getPod(podName)
            if(pod.status.phase == 'Succeeded') {
                k8sService.deletePod(podName)
            }else if(pod.status.phase == 'Running'){
                k8sService.deletePodWhenReachStatus(podName, 'Succeeded', blobConfig.podDeleteTimeout.toMillis())
            }
        }
        catch (Exception e) {
            log.warn ("Unable to delete pod=$podName - cause: ${e.message ?: e}", e)
        }
    }
}
