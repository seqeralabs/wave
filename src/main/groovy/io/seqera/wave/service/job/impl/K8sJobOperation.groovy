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

package io.seqera.wave.service.job.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.job.JobOperation
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
/**
 * Kubernetes implementation for {@link io.seqera.wave.service.job.JobService}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.build.k8s')
class K8sJobOperation implements JobOperation {

    @Inject
    private K8sService k8sService

    @Override
    void cleanup(JobSpec job) {
        k8sService.deleteJob(job.operationName)
    }

    @Override
    JobState status(JobSpec job) {
        final status = k8sService.getJobStatus(job.operationName)
        if( !status || !status.completed() ) {
            return new JobState(mapToStatus(status))
        }

        // Find the latest created pod among the pods associated with the job
        final pod = k8sService.getLatestPodForJob(job.operationName)
        if( !pod )
            throw new IllegalStateException("Missing carried pod for job: ${job.operationName}")

        // determine exit code and logs
        final exitCode = pod
                .status
                ?.containerStatuses
                ?.first()
                ?.state
                ?.terminated
                ?.exitCode
        final stdout = k8sService.logsPod(pod)
        return new JobState(mapToStatus(status), exitCode, stdout)
    }

    /**
     * Map Kubernetes job status to Transfer status
     * @param jobStatus
     * @return
     */
    static JobState.Status mapToStatus(K8sService.JobStatus jobStatus) {
        switch (jobStatus) {
            case K8sService.JobStatus.Pending:
                return JobState.Status.PENDING
            case K8sService.JobStatus.Running:
                return JobState.Status.RUNNING
            case K8sService.JobStatus.Succeeded:
                return JobState.Status.SUCCEEDED
            case K8sService.JobStatus.Failed:
                return JobState.Status.FAILED
            default:
                return JobState.Status.UNKNOWN
        }
    }
}