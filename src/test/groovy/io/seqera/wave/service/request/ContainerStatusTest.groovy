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

package io.seqera.wave.service.request

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.builder.BuildEntry
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerStatusTest extends Specification {

    def 'should create status from build entry'  () {
        given:
        def ts = Instant.now().minusSeconds(60)
        def request = Mock(BuildRequest)
        def result = Mock(BuildResult)

        when:
        def state1 = ContainerState.from(new BuildEntry(request, result))
        then:
        request.getStartTime() >> ts
        and:
        state1.startTime == ts
        state1.duration == null
        !state1.succeeded

        when:
        def state2 = ContainerState.from(new BuildEntry(request, result))
        then:
        request.getStartTime() >> ts
        result.getDuration() >> Duration.ofMinutes(1)
        result.succeeded() >> true
        and:
        state2.startTime == ts
        state2.duration >= Duration.ofMinutes(1)
        state2.succeeded
    }

    @Unroll
    def 'should create status from build record' () {
        given:
        def ts = Instant.now().minusSeconds(60)
        def build = Mock(WaveBuildRecord)

        when:
        def state = ContainerState.from(build)
        then:
        build.getStartTime() >> TIME
        build.duration >> DURATION
        build.succeeded() >> SUCCEEDED
        and:
        state.startTime == TIME
        state.duration == DURATION
        state.succeeded == SUCCEEDED

        where:
        TIME            | DURATION               | SUCCEEDED
        Instant.now()   | null                   | false
        Instant.now()   | Duration.ofSeconds(10) | true
    }

    def 'should create status from mirror entry'  () {
        given:
        def ts = Instant.now().minusSeconds(60)
        def request = Mock(MirrorRequest)
        def result = Mock(MirrorResult)

        when:
        def state1 = ContainerState.from(new MirrorEntry(request, result))
        then:
        request.getCreationTime() >> ts
        and:
        state1.startTime == ts
        state1.duration == null
        !state1.succeeded

        when:
        def state2 = ContainerState.from(new MirrorEntry(request, result))
        then:
        request.getCreationTime() >> ts
        result.getDuration() >> Duration.ofMinutes(1)
        result.succeeded() >> true
        and:
        state2.startTime == ts
        state2.duration == Duration.ofMinutes(1)
        state2.succeeded
    }

    @Unroll
    def 'should create status from mirror result' () {
        given:
        def mirror = Mock(MirrorResult)

        when:
        def state = ContainerState.from(mirror)
        then:
        mirror.getCreationTime() >> TIME
        mirror.duration >> DURATION
        mirror.succeeded() >> SUCCEEDED
        and:
        state.startTime == TIME
        state.duration == DURATION
        state.succeeded == SUCCEEDED

        where:
        TIME            | DURATION               | SUCCEEDED
        Instant.now()   | null                   | false
        Instant.now()   | Duration.ofSeconds(10) | true
    }
}
