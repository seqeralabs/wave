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
import java.time.Instant

import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

class MultiBuildEntryTest extends Specification {

    def 'should create multi-build entry'() {
        given:
        def identity = new PlatformId(new User(id: 1, email: 'foo@user.com'))
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

        when:
        def entry = MultiBuildEntry.of(request)

        then:
        entry.key == 'docker.io/wave:multi123'
        entry.requestId == request.multiBuildId
        entry.request == request
        !entry.done()
        entry.succeeded() == null
    }

    def 'should update with result'() {
        given:
        def identity = new PlatformId(new User(id: 1, email: 'foo@user.com'))
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

        when:
        def entry = MultiBuildEntry.of(request)
        then:
        !entry.done()

        when:
        def result = BuildResult.completed('bd-container123_0', 0, 'ok', Instant.now(), null)
        entry = entry.withResult(result)
        then:
        entry.done()
        entry.succeeded()
        entry.result == result
        entry.request == request
    }

    def 'should report failure'() {
        given:
        def identity = new PlatformId(new User(id: 1, email: 'foo@user.com'))
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

        when:
        def entry = MultiBuildEntry.of(request)
        def result = BuildResult.failed('bd-container123_0', 'something went wrong', Instant.now())
        entry = entry.withResult(result)

        then:
        entry.done()
        !entry.succeeded()
        entry.result.failed()
    }
}
