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

package io.seqera.wave.service.k8s

import spock.lang.Specification

import io.kubernetes.client.openapi.models.V1PodStatus
/**
 * Checks the bundled Kubernetes client model can parse the pod status payload
 * returned by recent Kubernetes versions. See COMP-2368.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class V1PodStatusParsingTest extends Specification {

    def 'should parse pod status carrying pod level allocated resources'() {
        given:
        // `status.allocatedResources` at pod level is returned by Kubernetes 1.34 and later
        def payload = '''
            {
              "phase": "Succeeded",
              "allocatedResources": { "cpu": "1", "memory": "2Gi" },
              "containerStatuses": [
                {
                  "name": "buildkit",
                  "ready": false,
                  "restartCount": 0,
                  "image": "moby/buildkit:v0.25.2-rootless",
                  "imageID": "moby/buildkit@sha256:123",
                  "state": { "terminated": { "exitCode": 0, "reason": "Completed" } }
                }
              ]
            }
            '''

        when:
        def status = V1PodStatus.fromJson(payload)

        then:
        noExceptionThrown()
        and:
        status.phase == 'Succeeded'
        status.containerStatuses.first().state.terminated.exitCode == 0
    }

}
