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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.transfer.Transfer
import io.seqera.wave.service.blob.transfer.Transfer.Status
import io.seqera.wave.service.blob.transfer.TransferStrategy
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.k8s.K8sService.JobStatus
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
@Requires(property = 'wave.blobCache.enabled', value = 'true')
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
    void transfer(BlobCacheInfo info, List<String> command) {
        // run the transfer job
        k8sService.transferJob(info.jobName, blobConfig.s5Image, command, blobConfig)
    }

    @Override
    void cleanup(BlobCacheInfo blob) {
        if( cleanup.shouldCleanup(blob.exitStatus) ) {
            CompletableFuture.supplyAsync (() -> k8sService.deleteJob(blob.jobName), executor)
        }
    }

    @Override
    Transfer status(BlobCacheInfo info) {
        final status = k8sService.getJobStatus(info.jobName)
        if( !status || !status.completed() ) {
            return new Transfer(mapToStatus(status))
        }

        // Find the latest created pod among the pods associated with the job
        final pod = k8sService.getLatestPodForJob(info.jobName)
        if( !pod )
            throw new IllegalStateException("Missing carried pod for job: ${info.jobName}")

        // determine exit code and logs
        final exitCode = pod
                .status
                ?.containerStatuses
                ?.first()
                ?.state
                ?.terminated
                ?.exitCode
        final stdout = k8sService.logsPod(pod)
        return new Transfer(mapToStatus(status), exitCode, stdout)
    }

    /**
     * Map Kubernetes job status to Transfer status
     * @param jobStatus
     * @return
     */
    static Status mapToStatus(JobStatus jobStatus) {
        switch (jobStatus) {
            case JobStatus.Pending:
                return Status.PENDING
            case JobStatus.Running:
                return Status.RUNNING
            case JobStatus.Succeeded:
                return Status.SUCCEEDED
            case JobStatus.Failed:
                return Status.FAILED
            default:
                return Status.UNKNOWN
        }
    }
}
