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

package io.seqera.wave.controller

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.logs.BuildLogServiceImpl
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerBuildControllerTest extends Specification {

    @MockBean(BuildLogServiceImpl)
    BuildLogService logsService() {
        Mock(BuildLogService)
    }


    @Inject
    @Client("/")
    HttpClient client

    @Inject PersistenceService persistenceService

    @Inject BuildLogService buildLogService

    def 'should get container build record' () {
        given:
        final build = new BuildRequest(
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
            .withBuildId('1')
        final result = new BuildResult(build.buildId, -1, "ok", Instant.now(), Duration.ofSeconds(3), null)
        final event = new BuildEvent(build, result)
        final entry = WaveBuildRecord.fromEvent(event)
        and:
        persistenceService.saveBuild(entry)
        when:
        def req = HttpRequest.GET("/v1alpha1/builds/${build.buildId}")
        def res = client.toBlocking().exchange(req, WaveBuildRecord)

        then:
        res.body().buildId == build.buildId

    }

    def 'should get container build log' () {
        given:
        def buildId = 'testbuildid1234'
        def LOGS = "test build log"
        def response = new StreamedFile(new ByteArrayInputStream(LOGS.bytes), MediaType.APPLICATION_OCTET_STREAM_TYPE)

        when:
        def req = HttpRequest.GET("/v1alpha1/builds/${buildId}/logs")
        def res = client.toBlocking().exchange(req, StreamedFile)

        then:
        1 * buildLogService.fetchLogStream(buildId) >> response
        and:
        res.code() == 200
        new String(res.bodyBytes) == LOGS
    }

}
