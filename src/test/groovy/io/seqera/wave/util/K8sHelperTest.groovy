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

package io.seqera.wave.util

import spock.lang.Specification

import java.time.OffsetDateTime

import io.kubernetes.client.openapi.models.V1ObjectMeta
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class K8sHelperTest extends Specification {

    def 'should get platform selector' () {
        expect:
        K8sHelper.getSelectorLabel(ContainerPlatform.of(PLATFORM), SELECTORS) == EXPECTED

        where:
        PLATFORM        | SELECTORS                                             | EXPECTED
        'amd64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['foo': '1']
        'arm64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['bar': '2']
        and:
        'amd64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'x86_64'        | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'arm64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['bar': '2']

    }

    def 'should check unmatched platform' () {
        expect:
        K8sHelper.getSelectorLabel(ContainerPlatform.of('amd64'), [:]) == [:]

        when:
        K8sHelper.getSelectorLabel(ContainerPlatform.of('amd64'), [arm64:'x=1'])
        then:
        def err = thrown(BadRequestException)
        err.message == "Unsupported container platform 'linux/amd64'"
    }

    def "should return the latest pod when multiple pods are present"() {
        given:
        def pod1 = new V1Pod().metadata(new V1ObjectMeta().creationTimestamp(OffsetDateTime.now().minusDays(1)))
        def pod2 = new V1Pod().metadata(new V1ObjectMeta().creationTimestamp(OffsetDateTime.now()))
        def allPods = new V1PodList().items(Arrays.asList(pod1, pod2))

        when:
        def latestPod = K8sHelper.findLatestPod(allPods)

        then:
        latestPod == pod2
    }

    def "should return the only pod when one pod is present"() {
        given:
        def pod = new V1Pod().metadata(new V1ObjectMeta().creationTimestamp(OffsetDateTime.now()))
        def allPods = new V1PodList().items(Collections.singletonList(pod))

        when:
        def latestPod = K8sHelper.findLatestPod(allPods)

        then:
        latestPod == pod
    }

}
