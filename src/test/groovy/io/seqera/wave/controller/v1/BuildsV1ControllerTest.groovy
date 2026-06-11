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

package io.seqera.wave.controller.v1

import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.v1.model.BuildStatusResponse
import io.seqera.wave.api.v1.model.Status
import io.seqera.wave.api.v1.model.WaveBuildRecord
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.logs.BuildLogServiceImpl
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Spock spec for {@link BuildsV1Controller} – covers all four /w1/builds/* routes.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class BuildsV1ControllerTest extends Specification {

    @Inject @Client('/') HttpClient client
    @Inject ContainerBuildService buildService
    @Inject BuildLogService buildLogService

    @MockBean(ContainerBuildService)
    ContainerBuildService mockBuildService() { Mock(ContainerBuildService) }

    @MockBean(BuildLogServiceImpl)
    BuildLogService mockBuildLogService() { Mock(BuildLogService) }

    // ----------------------------------------------------------------
    // Helper to build a minimal internal WaveBuildRecord
    // ----------------------------------------------------------------
    private io.seqera.wave.service.persistence.WaveBuildRecord makeRecord(String id) {
        new io.seqera.wave.service.persistence.WaveBuildRecord(
                buildId: id,
                dockerFile: 'FROM ubuntu:22.04',
                condaFile: null,
                targetImage: 'example.io/repo:tag',
                userName: 'tester',
                userEmail: 'tester@example.com',
                userId: 42L,
                requestIp: '1.2.3.4',
                startTime: Instant.parse('2025-01-01T00:00:00Z'),
                offsetId: '+00:00',
                duration: Duration.ofSeconds(30),
                exitStatus: 0,
                platform: 'linux/amd64',
                scanId: null,
                format: BuildFormat.DOCKER,
                digest: 'sha256:abc123',
        )
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id} – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id} returns 200 with mapped WaveBuildRecord'() {
        given:
        def id = 'build-001'
        buildService.getBuildRecord(id) >> makeRecord(id)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/builds/${id}"), WaveBuildRecord)

        then:
        resp.status == HttpStatus.OK
        resp.body().buildId == id
        resp.body().targetImage == 'example.io/repo:tag'
        resp.body().userName == 'tester'
        resp.body().userEmail == 'tester@example.com'
        resp.body().userId == 42L
        resp.body().requestIp == '1.2.3.4'
        resp.body().platform == 'linux/amd64'
        resp.body().dockerFile == 'FROM ubuntu:22.04'
        resp.body().digest == 'sha256:abc123'
        resp.body().exitStatus == 0
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id} – not found
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id} returns 404 when record not found'() {
        given:
        buildService.getBuildRecord('missing') >> null

        when:
        client.toBlocking().exchange(HttpRequest.GET('/w1/builds/missing'), WaveBuildRecord)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id}/status – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id}/status returns 200 with mapped BuildStatusResponse'() {
        given:
        def id = 'build-002'
        buildService.getBuildRecord(id) >> makeRecord(id)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/builds/${id}/status"), BuildStatusResponse)

        then:
        resp.status == HttpStatus.OK
        resp.body().id == id
        resp.body().status == Status.COMPLETED
        resp.body().succeeded == true
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id}/status – not found
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id}/status returns 404 when record not found'() {
        given:
        buildService.getBuildRecord('missing') >> null

        when:
        client.toBlocking().exchange(HttpRequest.GET('/w1/builds/missing/status'), BuildStatusResponse)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id}/logs – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id}/logs returns 200 with log text'() {
        given:
        def id = 'build-003'
        def logContent = 'Step 1/3: FROM ubuntu:22.04\nBuild successful'
        def streamedFile = new StreamedFile(
                new ByteArrayInputStream(logContent.bytes),
                MediaType.APPLICATION_OCTET_STREAM_TYPE)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/builds/${id}/logs"), String)

        then:
        1 * buildLogService.fetchLogStream(id) >> streamedFile
        resp.status == HttpStatus.OK
        resp.body() == logContent
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id}/logs – not found
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id}/logs returns 404 when log not found'() {
        given:
        def id = 'build-004'

        when:
        client.toBlocking().exchange(HttpRequest.GET("/w1/builds/${id}/logs"), String)

        then:
        1 * buildLogService.fetchLogStream(id) >> null
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id}/condalock – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id}/condalock returns 200 with lock text'() {
        given:
        def id = 'build-005'
        def condaContent = '@EXPLICIT\nhttps://conda.anaconda.org/conda-forge/linux-64/python-3.11.0.conda'
        def streamedFile = new StreamedFile(
                new ByteArrayInputStream(condaContent.bytes),
                MediaType.APPLICATION_OCTET_STREAM_TYPE)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/builds/${id}/condalock"), String)

        then:
        1 * buildLogService.fetchCondaLockStream(id) >> streamedFile
        resp.status == HttpStatus.OK
        resp.body() == condaContent
    }

    // ----------------------------------------------------------------
    // GET /w1/builds/{id}/condalock – not found
    // ----------------------------------------------------------------
    def 'GET /w1/builds/{id}/condalock returns 404 when lock not found'() {
        given:
        def id = 'build-006'

        when:
        client.toBlocking().exchange(HttpRequest.GET("/w1/builds/${id}/condalock"), String)

        then:
        1 * buildLogService.fetchCondaLockStream(id) >> null
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }
}
