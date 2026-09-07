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


import spock.lang.Specification
import spock.lang.Unroll

import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.models.V1ContainerState
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1ContainerStatus
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodStatus
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.k8s.K8sService
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class K8SJobOperationTest extends Specification {

    K8sService k8sService = Mock(K8sService)
    K8sJobOperation strategy = new K8sJobOperation(k8sService: k8sService)

    def 'status should return correct status when job is not completed'() {
        given:
        def job = Mock(JobSpec)
        and:
        k8sService.getJobStatus(job.operationName) >> K8sService.JobStatus.Running

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.RUNNING
    }

    void 'status should return correct transfer status when pods are created'() {
        given:
        def job = Mock(JobSpec)
        and:
        def status = new V1PodStatus(phase: "Succeeded", containerStatuses: [new V1ContainerStatus( state: new V1ContainerState(terminated: new V1ContainerStateTerminated(exitCode: 0)))])
        def pod = new V1Pod(metadata: [name: job.operationName], status: status)
        and:
        k8sService.getJobStatus(job.operationName) >> K8sService.JobStatus.Succeeded
        k8sService.getLatestPodForJob(job.operationName) >> pod
        k8sService.logsPod(pod) >> "transfer successful"

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.SUCCEEDED
        result.exitCode == 0
        result.stdout == "transfer successful"
    }

    def 'status should return failed transfer when no pods are created'() {
        given:
        def job = Mock(JobSpec)
        and:
        def status = new V1PodStatus(phase: "Failed", containerStatuses: [new V1ContainerStatus( state: new V1ContainerState(terminated: new V1ContainerStateTerminated(exitCode: 255)))])
        def pod = new V1Pod(metadata: [name: job.operationName], status: status)
        and:
        k8sService.getLatestPodForJob(job.operationName) >> pod
        k8sService.getJobStatus(job.operationName) >> K8sService.JobStatus.Failed

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.FAILED
    }

    def 'status should handle null job status'() {
        given:
        def job = Mock(JobSpec)
        and:
        k8sService.getJobStatus(job.operationName) >> null

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.UNKNOWN
    }

    def 'status should fall back to job status when the carrier pod cannot be parsed'() {
        given:
        def job = Mock(JobSpec)
        and:
        k8sService.getJobStatus(job.operationName) >> K8sService.JobStatus.Succeeded
        k8sService.getLatestPodForJob(job.operationName) >> {
            throw new IllegalArgumentException("The field `allocatedResources` in the JSON string is not defined in the `V1PodStatus` properties.")
        }

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.SUCCEEDED
        result.exitCode == 0
        result.succeeded()
    }

    def 'status should report failure when the carrier pod cannot be parsed and the job failed'() {
        given:
        def job = Mock(JobSpec)
        and:
        k8sService.getJobStatus(job.operationName) >> K8sService.JobStatus.Failed
        k8sService.getLatestPodForJob(job.operationName) >> {
            throw new IllegalArgumentException("The field `allocatedResources` in the JSON string is not defined in the `V1PodStatus` properties.")
        }

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.FAILED
        result.exitCode == 255
        !result.succeeded()
    }

    def 'status should report success when the pod status carries a field unknown to the client'() {
        given:
        def job = Mock(JobSpec)
        and:
        k8sService.getJobStatus(job.operationName) >> K8sService.JobStatus.Succeeded
        // deserialize through the real k8s client model, so the guard is exercised with the
        // actual exception raised by a Kubernetes version newer than the bundled client
        k8sService.getLatestPodForJob(job.operationName) >> {
            V1PodStatus.fromJson('{"phase":"Succeeded","someFutureK8sField":"x"}')
        }

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.SUCCEEDED
        result.succeeded()
    }

    def 'status should not swallow api errors from the pod lookup'() {
        given:
        def job = Mock(JobSpec)
        and:
        k8sService.getJobStatus(job.operationName) >> K8sService.JobStatus.Succeeded
        k8sService.getLatestPodForJob(job.operationName) >> {
            throw new ApiException(403, 'pods is forbidden: cannot list resource "pods"')
        }

        when:
        strategy.status(job)
        then:
        // RBAC denials and API timeouts surface as ApiException and must not be
        // mistaken for a version skew, ie. they must not reach the fallback path.
        // Spock proxies the interface, which wraps the checked exception, hence the cause check
        def e = thrown(Throwable)
        (e instanceof ApiException ? e : e.cause) instanceof ApiException
    }

    @Unroll
    def "mapToStatus should return correct transfer status for jobStatus #JOB_STATUS that is #TRANSFER_STATUS"() {
        expect:
        K8sJobOperation.mapToStatus(JOB_STATUS) == TRANSFER_STATUS

        where:
        JOB_STATUS                      | TRANSFER_STATUS
        K8sService.JobStatus.Pending    | JobState.Status.PENDING
        K8sService.JobStatus.Running    | JobState.Status.RUNNING
        K8sService.JobStatus.Succeeded  | JobState.Status.SUCCEEDED
        K8sService.JobStatus.Failed     | JobState.Status.FAILED
        null                            | JobState.Status.UNKNOWN
    }

}
