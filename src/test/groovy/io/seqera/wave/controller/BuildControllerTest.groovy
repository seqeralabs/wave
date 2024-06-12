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
import java.time.temporal.ChronoUnit

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
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
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.util.ContainerHelper

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class BuildControllerTest extends Specification {

    @MockBean(BuildLogServiceImpl)
    BuildLogService logsService() {
        Mock(BuildLogService)
    }


    @Inject
    @Client("/")
    HttpClient client

    @Inject
    PersistenceService persistenceService

    @Inject
    BuildLogService buildLogService

    def 'should get container build record' () {
        given:
        final repo = "foo.com/repo"
        final containerFile = 'FROM foo:latest'
        final format = BuildFormat.DOCKER
        final platform = ContainerPlatform.of('amd64')
        final containerId = ContainerHelper.makeContainerId(containerFile, null, null, platform, 'buildrepo', null)
        final targetImage = ContainerHelper.makeTargetImage(format, repo, containerId, null, null, null)
        final build = new BuildRequest(
                containerId,
                containerFile,
                null,
                null,
                Path.of("/some/path"),
                targetImage,
                PlatformId.NULL,
                platform,
                'cacherepo',
                "1.2.3.4",
                '{"config":"json"}',
                null,
                null,
                'scan12345',
                null,
                format)
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
        def res = client.toBlocking().exchange(req, String)

        then:
        1 * buildLogService.fetchLogStream(buildId) >> response
        and:
        res.code() == 200
        res.body() == LOGS
    }


    def 'should get container status' () {
        given:
        def build1 = new WaveBuildRecord(
                buildId: 'test1',
                dockerFile: 'test1',
                condaFile: 'test1',
                targetImage: 'testImage1',
                userName: 'testUser1',
                userEmail: 'test1@xyz.com',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now().minus(1, ChronoUnit.DAYS) )
        and:
        persistenceService.saveBuild(build1)
        sleep(500)

        when:
        def req = HttpRequest.GET("/v1alpha1/builds/${build1.buildId}/status")
        def res = client.toBlocking().exchange(req, BuildStatusResponse)
        then:
        res.status() == HttpStatus.OK
        res.body().id == build1.buildId
        res.body().status == BuildStatusResponse.Status.PENDING

        when:
        client.toBlocking().exchange(HttpRequest.GET("/v1alpha1/builds/0000/status"), BuildStatusResponse)
        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

}
