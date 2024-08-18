/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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


import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.Executors

import io.kubernetes.client.openapi.models.V1ContainerState
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1ContainerStatus
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.job.JobId
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.k8s.K8sService.JobStatus
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.k8s.K8sService
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class KubeJobStrategyTest extends Specification {

    K8sService k8sService = Mock(K8sService)
    BlobCacheConfig blobConfig = new BlobCacheConfig(s5Image: 's5cmd', transferTimeout: Duration.ofSeconds(10), retryAttempts: 3)
    CleanupStrategy cleanup = new CleanupStrategy(buildConfig: new BuildConfig(cleanup: "OnSuccess"))
    KubeTransferStrategy strategy = new KubeTransferStrategy(k8sService: k8sService, blobConfig: blobConfig, cleanup: cleanup, executor: Executors.newSingleThreadExecutor())

    def "transfer should start a transferJob"() {
        given:
        def info = BlobCacheInfo.create("https://test.com/blobs", "https://test.com/bucket/blobs", null, null)
        def command = ["transfer", "blob"]
        final jobName = "job-123"
        def podName = "$jobName-abc".toString()
        def pod = new V1Pod(metadata: [name: podName, creationTimestamp: OffsetDateTime.now()])
        pod.status = new V1PodStatus(phase: "Succeeded")
        def podList = new V1PodList(items: [pod])
        k8sService.launchJob(_, _, _, _) >> new V1Job(metadata: [name: jobName])
        k8sService.waitJob(_, _) >> podList
        k8sService.getPod(_) >> pod
        k8sService.waitPodCompletion(_, _) >> 0
        k8sService.logsPod(_) >> "transfer successful"

        when:
        strategy.launchJob(podName, command)

        then:
        1 * k8sService.launchJob(podName, blobConfig.s5Image, command, blobConfig)
    }

    def 'status should return correct status when job is not completed'() {
        given:
        def job = JobId.transfer('foo')
        and:
        k8sService.getJobStatus(job.schedulerId) >> K8sService.JobStatus.Running

        when:
        def result = strategy.status(job)
        then:
        result.status == JobState.Status.RUNNING
    }

    void 'status should return correct transfer status when pods are created'() {
        given:
        def job = JobId.transfer('foo')
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
        def job = JobId.transfer('foo')
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
        def job = JobId.transfer('foo')
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
        KubeTransferStrategy.mapToStatus(JOB_STATUS) == TRANSFER_STATUS

        where:
        JOB_STATUS          | TRANSFER_STATUS
        JobStatus.Pending   | JobState.Status.PENDING
        JobStatus.Running   | JobState.Status.RUNNING
        JobStatus.Succeeded | JobState.Status.SUCCEEDED
        JobStatus.Failed    | JobState.Status.FAILED
        null                | JobState.Status.UNKNOWN
    }
}
