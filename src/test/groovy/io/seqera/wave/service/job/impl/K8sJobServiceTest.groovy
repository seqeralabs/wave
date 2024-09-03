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

import io.kubernetes.client.openapi.models.V1ContainerState
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1ContainerStatus
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodStatus
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.job.spec.BuildJobSpec
import io.seqera.wave.service.job.spec.TransferJobSpec
import io.seqera.wave.service.k8s.K8sService
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class K8sJobServiceTest extends Specification {

    K8sService k8sService = Mock(K8sService)
    K8sJobService strategy = new K8sJobService(k8sService: k8sService)

    def 'status should return correct status when job is not completed'() {
        given:
        def job = Mock(BuildJobSpec)
        and:
        k8sService.getJobStatus(job.schedulerId) >> K8sService.JobStatus.Running

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.RUNNING
    }

    void 'status should return correct transfer status when pods are created'() {
        given:
        def job = Mock(TransferJobSpec)
        and:
        def status = new V1PodStatus(phase: "Succeeded", containerStatuses: [new V1ContainerStatus( state: new V1ContainerState(terminated: new V1ContainerStateTerminated(exitCode: 0)))])
        def pod = new V1Pod(metadata: [name: job.schedulerId], status: status)
        and:
        k8sService.getJobStatus(job.schedulerId) >> K8sService.JobStatus.Succeeded
        k8sService.getLatestPodForJob(job.schedulerId) >> pod
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
        def job = Mock(TransferJobSpec)
        and:
        def status = new V1PodStatus(phase: "Failed")
        def pod = new V1Pod(metadata: [name: job.schedulerId], status: status)
        and:
        k8sService.getLatestPodForJob(job.schedulerId) >> pod
        k8sService.getJobStatus(job.schedulerId) >> K8sService.JobStatus.Failed

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.FAILED
    }

    def 'status should handle null job status'() {
        given:
        def job = Mock(TransferJobSpec)
        and:
        k8sService.getJobStatus(job.schedulerId) >> null

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.UNKNOWN
    }

    @Unroll
    def "mapToStatus should return correct transfer status for jobStatus #JOB_STATUS that is #TRANSFER_STATUS"() {
        expect:
        K8sJobService.mapToStatus(JOB_STATUS) == TRANSFER_STATUS

        where:
        JOB_STATUS                      | TRANSFER_STATUS
        K8sService.JobStatus.Pending    | JobState.Status.PENDING
        K8sService.JobStatus.Running    | JobState.Status.RUNNING
        K8sService.JobStatus.Succeeded  | JobState.Status.SUCCEEDED
        K8sService.JobStatus.Failed     | JobState.Status.FAILED
        null                            | JobState.Status.UNKNOWN
    }

}
