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

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.transfer.Transfer
import io.seqera.wave.service.k8s.K8sService.JobStatus
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.k8s.K8sService
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class KubeTransferStrategyTest extends Specification {

    K8sService k8sService = Mock(K8sService)
    BlobCacheConfig blobConfig = new BlobCacheConfig(s5Image: 's5cmd', transferTimeout: Duration.ofSeconds(10), retryAttempts: 3)
    CleanupStrategy cleanup = new CleanupStrategy(buildConfig: new BuildConfig(cleanup: "OnSuccess"))
    KubeTransferStrategy strategy = new KubeTransferStrategy(k8sService: k8sService, blobConfig: blobConfig, cleanup: cleanup, executor: Executors.newSingleThreadExecutor())

    def "transfer should start a transferJob"() {
        given:
        def info = BlobCacheInfo.create("https://test.com/blobs", "https://test.com/bucket/blobs", null, null)
        def command = ["transfer", "blob"]
        final jobName = "job-123"
        def podName = "$jobName-abc"
        def pod = new V1Pod(metadata: [name: podName, creationTimestamp: OffsetDateTime.now()])
        pod.status = new V1PodStatus(phase: "Succeeded")
        def podList = new V1PodList(items: [pod])
        k8sService.transferJob(_, _, _, _) >> new V1Job(metadata: [name: jobName])
        k8sService.waitJob(_, _) >> podList
        k8sService.getPod(_) >> pod
        k8sService.waitPodCompletion(_, _) >> 0
        k8sService.logsPod(_) >> "transfer successful"

        when:
        strategy.transfer(info, command)

        then:
        1 * k8sService.transferJob(info.jobName, blobConfig.s5Image, command, blobConfig)
    }

    def 'status should return correct status when job is not completed'() {
        given:
        def info = BlobCacheInfo.create("https://test.com/blobs", "https://test.com/bucket/blobs", null, null)
        k8sService.getJobStatus(info.jobName) >> K8sService.JobStatus.Running

        when:
        def result = strategy.status(info)

        then:
        result.status == Transfer.Status.RUNNING
    }


    void 'status should return correct transfer status when pods are created'() {
        given:
        def info = BlobCacheInfo.create("https://test.com/blobs", "https://test.com/bucket/blobs", null, null)
        def pod = new V1Pod(metadata: [name: "pod-123"], status: new V1PodStatus(phase: "Succeeded"))
        def podList = new V1PodList(items: [pod])
        k8sService.getJobStatus(_) >> K8sService.JobStatus.Succeeded
        k8sService.getJob(_) >> new V1Job()
        k8sService.waitJob(_, _) >> podList
        k8sService.getPod("pod-123") >> pod
        k8sService.waitPodCompletion(_, _) >> 0
        k8sService.logsPod(_) >> "transfer successful"

        when:
        def result = strategy.status(info)

        then:
        result.status == Transfer.Status.SUCCEEDED
        result.exitCode == 0
        result.stdout == "transfer successful"
    }

    def 'status should return failed transfer when no pods are created'() {
        given:
        def info = BlobCacheInfo.create("https://test.com/blobs", "https://test.com/bucket/blobs", null, null)
        k8sService.getJobStatus(_) >> K8sService.JobStatus.Succeeded
        k8sService.getJob(_) >> new V1Job()
        k8sService.waitJob(_, _) >> new V1PodList(items: [])

        when:
        def result = strategy.status(info)

        then:
        result.status == Transfer.Status.FAILED
    }

    def 'status should handle null job status'() {
        given:
        def info = BlobCacheInfo.create("https://test.com/blobs", "https://test.com/bucket/blobs", null, null)
        k8sService.getJobStatus(info.id) >> null

        when:
        def result = strategy.status(info)

        then:
        result.status == Transfer.Status.UNKNOWN
    }

    @Unroll
    def "mapToStatus should return correct transfer status for jobStatus #JOB_STATUS that is #TRANSFER_STATUS"() {
        expect:
        KubeTransferStrategy.mapToStatus(JOB_STATUS) == TRANSFER_STATUS

        where:
        JOB_STATUS          | TRANSFER_STATUS
        JobStatus.Pending   | Transfer.Status.PENDING
        JobStatus.Running   | Transfer.Status.RUNNING
        JobStatus.Succeeded | Transfer.Status.SUCCEEDED
        JobStatus.Failed    | Transfer.Status.FAILED
        null                | Transfer.Status.UNKNOWN
    }
}
