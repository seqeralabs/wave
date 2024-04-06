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

package io.seqera.wave.service.persistence

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.JacksonHelper
import io.seqera.wave.api.BuildStatusResponse

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class WaveBuildRecordTest extends Specification {

    def 'should serialise-deserialize build record' () {
        given:
        final request = new BuildRequest(
                'FROM foo:latest',
                Path.of("/some/path"),
                "buildrepo",
                'conda::recipe',
                'some-spack-recipe',
                BuildFormat.DOCKER,
                PlatformId.NULL,
                null,
                null,
                ContainerPlatform.of('amd64'),
                '{auth}',
                'docker.io/my/repo',
                '12345',
                "1.2.3.4",
                null )
        final result = new BuildResult(request.buildId, -1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        final event = new BuildEvent(request, result)
        final record = WaveBuildRecord.fromEvent(event)

        when:
        def json = JacksonHelper.toJson(record)
        then:
        JacksonHelper.fromJson(json, WaveBuildRecord) == record

    }

    def 'should convert to status response' () {
        given:
        final request = new BuildRequest(
                'FROM foo:latest',
                Path.of("/some/path"),
                "buildrepo",
                null,
                null,
                BuildFormat.DOCKER,
                PlatformId.NULL,
                null,
                null,
                ContainerPlatform.of('amd64'),
                '{auth}',
                'docker.io/my/repo',
                '12345',
                "1.2.3.4",
                null )
                .withBuildId('123')

        and:
        final event = new BuildEvent(request)
        final record = WaveBuildRecord.fromEvent(event)
        when:
        def resp = record.toStatusResponse()
        then:
        resp.id == request.buildId
        resp.status == BuildStatusResponse.Status.PENDING
        resp.startTime == request.startTime
        and:
        resp.duration == null
        resp.succeeded == null

        when:
        final result2 = new BuildResult(request.buildId, 1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        final event2 = new BuildEvent(request, result2)
        final record2 = WaveBuildRecord.fromEvent(event2)
        final resp2 = record2.toStatusResponse()
        then:
        resp2.duration == record2.duration
        resp2.succeeded == false

        when:
        final result3 = new BuildResult(request.buildId, 0, "ok", Instant.now(), Duration.ofSeconds(3), null)
        final event3 = new BuildEvent(request, result3)
        final record3 = WaveBuildRecord.fromEvent(event3)
        final resp3 = record3.toStatusResponse()
        then:
        resp3.duration == record3.duration
        resp3.succeeded == true

    }

}
