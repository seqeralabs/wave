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

package io.seqera.wave.service.builder

import spock.lang.Specification

import java.time.Duration

import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

class MultiBuildRequestTest extends Specification {

    def 'should create multi-build request'() {
        given:
        def identity = new PlatformId(new User(id: 1, email: 'foo@user.com'))

        when:
        def request = MultiBuildRequest.create(
                'container123',
                'docker.io/wave:multi123',
                'bd-container123_0',
                'docker.io/wave:amd64',
                'docker.io/wave:arm64',
                false,
                false,
                identity,
                Duration.ofMinutes(5)
        )

        then:
        request.multiBuildId.startsWith(MultiBuildRequest.ID_PREFIX)
        request.targetImage == 'docker.io/wave:multi123'
        request.containerId == 'container123'
        request.buildId == 'bd-container123_0'
        request.amd64TargetImage == 'docker.io/wave:amd64'
        request.arm64TargetImage == 'docker.io/wave:arm64'
        request.amd64Cached == false
        request.arm64Cached == false
        request.identity == identity
        request.creationTime != null
        request.maxDuration == Duration.ofMinutes(5)
    }

    def 'should create from map'() {
        when:
        def request = MultiBuildRequest.of(
                multiBuildId: 'mb-abc123',
                targetImage: 'docker.io/wave:multi',
                containerId: 'cid',
                buildId: 'bd-cid_0',
                amd64TargetImage: 'docker.io/wave:amd64',
                arm64TargetImage: 'docker.io/wave:arm64',
                amd64Cached: true,
                arm64Cached: false,
                maxDuration: Duration.ofMinutes(10)
        )

        then:
        request.multiBuildId == 'mb-abc123'
        request.targetImage == 'docker.io/wave:multi'
        request.amd64Cached == true
        request.arm64Cached == false
    }
}
