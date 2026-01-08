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

package io.seqera.wave.service.mirror.strategy

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.configuration.MirrorConfig
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.mirror.MirrorRequest

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class KubeMirrorStrategyTest extends Specification {

    def "should use noarch node selector for mirror job"() {
        given:
        def k8sService = Mock(K8sService)
        def strategy = new KubeMirrorStrategy(
                config: Mock(MirrorConfig) {
                    getSkopeoImage() >> 'quay.io/skopeo/stable:latest'
                },
                k8sService: k8sService,
                nodeSelectorMap: ['linux/amd64': 'service=wave-build',
                                  'linux/arm64': 'service=wave-build-arm64',
                                  'noarch': 'service=wave-mirror']
        )
        def request = Mock(MirrorRequest) {
            getWorkDir() >> Path.of('/tmp/work')
            getAuthJson() >> null
        }

        when:
        strategy.mirrorJob('job-123', request)

        then:
        1 * k8sService.launchMirrorJob('job-123', 'quay.io/skopeo/stable:latest', _, Path.of('/tmp/work'), null, _,
                ['service':'wave-mirror'])
    }

    def "should use empty selector when noarch is not configured"() {
        given:
        def k8sService = Mock(K8sService)
        def strategy = new KubeMirrorStrategy(
                config: Mock(MirrorConfig) {
                    getSkopeoImage() >> 'quay.io/skopeo/stable:latest'
                },
                k8sService: k8sService,
                nodeSelectorMap: ['linux/amd64': 'service=wave-build',
                                  'linux/arm64': 'service=wave-build-arm64']
        )
        def request = Mock(MirrorRequest) {
            getWorkDir() >> Path.of('/tmp/work')
            getAuthJson() >> null
        }

        when:
        strategy.mirrorJob('job-456', request)

        then:
        1 * k8sService.launchMirrorJob('job-456', 'quay.io/skopeo/stable:latest', _, Path.of('/tmp/work'), null, _,
                [:])
    }

}
