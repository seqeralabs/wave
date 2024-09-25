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

import java.time.Duration
import java.time.OffsetDateTime

import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.kubernetes.client.openapi.models.V1PodStatus
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobState
import io.seqera.wave.service.k8s.K8sService
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class KubeTransferStrategyTest extends Specification {

    K8sService k8sService = Mock(K8sService)
    BlobCacheConfig blobConfig = new BlobCacheConfig(s5Image: 's5cmd', transferTimeout: Duration.ofSeconds(10), retryAttempts: 3)
    KubeTransferStrategy strategy = new KubeTransferStrategy(k8sService: k8sService, blobConfig: blobConfig)

    def "transfer should start a transferJob"() {
        given:
        def info = BlobState.create("https://test.com/blobs", "https://test.com/bucket/blobs", null, null)
        def command = ["transfer", "blob"]
        final jobName = "job-123"
        def podName = "$jobName-abc".toString()
        def pod = new V1Pod(metadata: [name: podName, creationTimestamp: OffsetDateTime.now()])
        pod.status = new V1PodStatus(phase: "Succeeded")
        def podList = new V1PodList(items: [pod])
        k8sService.launchTransferJob(_, _, _, _) >> new V1Job(metadata: [name: jobName])
        k8sService.waitJob(_, _) >> podList
        k8sService.getPod(_) >> pod
        k8sService.waitPodCompletion(_, _) >> 0
        k8sService.logsPod(_) >> "transfer successful"

        when:
        strategy.launchJob(podName, command)

        then:
        1 * k8sService.launchTransferJob(podName, blobConfig.s5Image, command, blobConfig)
    }

}
