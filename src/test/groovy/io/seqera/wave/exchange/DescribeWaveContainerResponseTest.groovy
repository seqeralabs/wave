/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.exchange

import java.time.Instant

import spock.lang.Specification
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.util.JacksonHelper

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 */
class DescribeWaveContainerResponseTest extends Specification {

    def 'should describe user identity without name or email'() {
        given:
        final request = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                towerWorkspaceId: 100,
                containerImage: 'hello-world',
                containerPlatform: ContainerPlatform.DEFAULT.toString(),
                fingerprint: 'xyz',
                timestamp: Instant.parse('2026-01-01T00:00:00Z').toString())
        and:
        final user = new User(id: 1, userName: 'api-user-name', email: 'api-user-name@example.com')
        final data = ContainerRequest.of(
                requestId: 'cr-12345',
                identity: new PlatformId(user, 100),
                containerImage: 'docker.io/library/hello-world:latest')
        final record = new WaveContainerRecord(
                request,
                data,
                'https://wave.io/wt/cr-12345/library/hello-world:latest',
                '127.0.0.1',
                Instant.parse('2026-01-02T00:00:00Z'))

        when:
        final response = DescribeWaveContainerResponse.create('cr-12345', record)
        final json = JacksonHelper.toJson(response)

        then:
        response.request.user.id == 1
        response.request.workspaceId == 100
        response.request.towerEndpoint == 'https://tower.nf'
        and:
        json.contains('"user":{"id":1}')
        json.contains('"workspaceId":100')
        json.contains('"towerEndpoint":"https://tower.nf"')
        !json.contains('api-user-name')
        !json.contains('api-user-name@example.com')
        !json.contains('userName')
        !json.contains('email')
    }
}
