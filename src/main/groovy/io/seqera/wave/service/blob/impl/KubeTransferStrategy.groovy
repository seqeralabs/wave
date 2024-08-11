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
import io.seqera.wave.service.blob.transfer.TransferStrategy
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.util.K8sHelper
import jakarta.inject.Inject
import jakarta.inject.Named
import io.seqera.wave.service.k8s.K8sService.JobStatus
/**
 * Implements {@link TransferStrategy} that runs s5cmd using a
 * Kubernetes job
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.build.k8s')
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
        cleanupJob(blob.jobName, blob.exitStatus)
    }

    protected void cleanupJob(String jobName, Integer exitCode) {
        if( cleanup.shouldCleanup(exitCode) ) {
            CompletableFuture.supplyAsync (() -> k8sService.deleteJob(jobName), executor)
        }
    }

    @Override
    Transfer status(BlobCacheInfo info) {
        final status = k8sService.getJobStatus(info.jobName)
        if( !status || !status.completed() ) {
            return new Transfer(mapToStatus(status))
        }

        final job = k8sService.getJob(info.jobName)
        final timeout = 1_000
        final podList = k8sService.waitJob(job, timeout)
        final size = podList?.items?.size() ?: 0
        // verify the upload pod has been created
        if( size < 1 ) {
            log.error "== Blob cache transfer failed - unable to schedule upload job: $info"
            return new Transfer(Status.FAILED)
        }
        // Find the latest created pod among the pods associated with the job
        final latestPod = K8sHelper.findLatestPod(podList)
        final pod = k8sService.getPod(latestPod.metadata.name)
        final exitCode = k8sService.waitPodCompletion(pod, timeout)
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
