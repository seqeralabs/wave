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

import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodStatus
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.k8s.K8sService
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class KubeTransferStrategyTest extends Specification {

    K8sService k8sService = Mock(K8sService)
    BlobCacheConfig blobConfig = new BlobCacheConfig(s5Image: 's5cmd', transferTimeout: Duration.ofSeconds(10))
    KubeTransferStrategy strategy = new KubeTransferStrategy(k8sService: k8sService, blobConfig: blobConfig)

    def "transfer should complete successfully with valid inputs"() {
        given:
        def uri = "s3://bucket/file.txt"
        def info = BlobCacheInfo.create(uri, null, null)
        def command = ["s5cmd", "cp", uri, "/local/path"]
        k8sService.transferContainer(_, blobConfig.s5Image, command, blobConfig) >> new V1Pod(status: new V1PodStatus(phase: 'Succeeded'))
        k8sService.getPod(_) >> new V1Pod(status: new V1PodStatus(phase: 'Succeeded'))
        k8sService.waitPod(_, _) >> new V1ContainerStateTerminated(exitCode: 0)
        k8sService.logsPod(_) >> "Transfer completed"

        when:
        def result = strategy.transfer(info, command)

        then:
        result.succeeded()
        result.exitStatus == 0
        result.logs == "Transfer completed"
    }

    def "transfer should fail when pod execution exceeds timeout"() {
        given:
        def uri = "s3://bucket/file.txt"
        def info = BlobCacheInfo.create(uri, null, null)
        def command = ["s5cmd", "cp", uri, "/local/path"]
        k8sService.transferContainer(_, blobConfig.s5Image, command, blobConfig) >> new V1Pod(status: new V1PodStatus(phase: 'Running'))
        k8sService.waitPod(_, blobConfig.transferTimeout.toMillis()) >> new V1ContainerStateTerminated(exitCode: 1)
        k8sService.logsPod(_) >> "Transfer timeout"

        when:
        def result = strategy.transfer(info, command)

        then:
        result.failed("Transfer timeout")
        result.logs == "Transfer timeout"
    }

    def "transfer should handle exception during transfer process"() {
        given:
        def uri = "s3://bucket/file.txt"
        def info = BlobCacheInfo.create(uri, null, null)
        def command = ["s5cmd", "cp", uri, "/local/path"]
        def podName = "transfer-unique-hash"
        k8sService.transferContainer(podName, blobConfig.s5Image, command, blobConfig) >> { throw new Exception("K8s error") }

        when:
        def result = strategy.transfer(info, command)

        then:
        result.failed("Error while scanning with id: s3://bucket/file.txt - cause: K8s error")
    }

    def "cleanup should delete completed pod"() {
        given:
        def podName = "transfer-unique-hash"
        k8sService.getPod(podName) >> new V1Pod(status: new V1PodStatus(phase: 'Succeeded'))

        when:
        strategy.cleanup(podName)

        then:
        1 * k8sService.deletePod(podName)
    }

    def "cleanup should not delete Failed pod"() {
        given:
        def podName = "transfer-unique-hash"
        k8sService.getPod(podName) >> new V1Pod(status: new V1PodStatus(phase: 'Failed'))

        when:
        strategy.cleanup(podName)

        then:
        0 * k8sService.deletePod(podName)
    }
}
