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
import io.kubernetes.client.openapi.models.V1Pod
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.seqera.util.trace.TraceElapsedTime
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

    @Inject
    @Nullable
    private MeterRegistry meterRegistry

    @Override
    void cleanup(JobSpec job) {
        k8sService.deleteJob(job.operationName)
    }

    @Override
    void cleanup(String jobName) {
        k8sService.deleteJob(jobName)
    }

    @Override
    @TraceElapsedTime(thresholdMillis = '${wave.trace.k8s.threshold:200}')
    JobState status(JobSpec job) {
        final status = k8sService.getJobStatus(job.operationName)
        if( !status || !status.completed() ) {
            return new JobState(mapToStatus(status))
        }

        // Find the latest created pod among the pods associated with the job
        V1Pod pod
        try {
            pod = k8sService.getLatestPodForJob(job.operationName)
        }
        catch( IllegalArgumentException e ) {
            // the Kubernetes client models reject any attribute added by a Kubernetes version
            // newer than the bundled client knows about, eg. pod level `status.allocatedResources`
            // introduced by Kubernetes 1.34. Without this guard the exception would escape and the
            // job would be reported as failed even when it completed successfully. Determine the
            // state from the Job level status instead (tho logs will be lost). See COMP-2368.
            // Note: only the model validation error is caught here - RBAC denials and API timeouts
            // are raised as `ApiException` and must keep propagating
            log.warn "K8s unable to parse carrier pod for job ${job.operationName} with status=${status} - likely a Kubernetes client version skew; cause: ${e.message}"
            meterRegistry?.counter('wave.k8s.pod.parse.errors')?.increment()
            return jobLevelState(status, "(logs not available - unable to parse the carrier pod status)")
        }
        if( !pod ) {
            // in some circumstances the pod carrier pod cannot be retried, most likely
            // due to a node disruption. in this case determine the state to be returned
            // by using the JobStatus information (tho logs will be lost)
            log.warn "K8s missing carrier pod for job ${job.operationName} with status=${status}"
            return jobLevelState(status, "(logs not available)")
        }

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
     * Determine the job state using the Kubernetes *Job* level status alone, ie. when the carrier
     * pod is not available. The container exit code and the job logs cannot be determined in this
     * case, therefore a conventional exit code is used for a failed job.
     *
     * @param status The Kubernetes job status
     * @param msg The message to be used in place of the job logs
     * @return The corresponding {@link JobState}
     */
    protected static JobState jobLevelState(K8sService.JobStatus status, String msg) {
        return switch (status) {
            case K8sService.JobStatus.Succeeded -> JobState.succeeded(msg)
            case K8sService.JobStatus.Failed -> JobState.failed(255, msg)
            default -> JobState.unknown(msg)
        }
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
