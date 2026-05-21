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
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.v1.model.ContainerMirrorResponse
import io.seqera.wave.api.v1.model.Status
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.mirror.ContainerMirrorServiceImpl
import io.seqera.wave.service.mirror.MirrorResult
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Spock spec for {@link MirrorsV1Controller} – covers both /w1/mirrors/* routes.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class MirrorsV1ControllerTest extends Specification {

    @Inject @Client('/') HttpClient client
    @Inject ContainerMirrorService mirrorService

    @MockBean(ContainerMirrorServiceImpl)
    ContainerMirrorService mockMirrorService() { Mock(ContainerMirrorService) }

    // ----------------------------------------------------------------
    // Helper to build a minimal internal MirrorResult
    // ----------------------------------------------------------------
    private MirrorResult makeResult(String id) {
        // MirrorResult is @Canonical with final fields – use positional constructor
        // field order: mirrorId, digest, sourceImage, targetImage, platform,
        //              creationTime, offsetId, userName, userEmail, userId,
        //              scanId, status, duration, exitCode, logs
        new MirrorResult(
                id,
                'sha256:abc123',
                'docker.io/library/ubuntu:22.04',
                'example.io/mirror/ubuntu:22.04',
                ContainerPlatform.of('linux/amd64'),
                Instant.parse('2025-01-01T00:00:00Z'),
                '+00:00',
                'tester',
                'tester@example.com',
                42L,
                null,
                MirrorResult.Status.COMPLETED,
                Duration.ofSeconds(15),
                0,
                'Mirror completed successfully'
        )
    }

    // ----------------------------------------------------------------
    // GET /w1/mirrors/{id} – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/mirrors/{id} returns 200 with mapped ContainerMirrorResponse'() {
        given:
        def id = 'mirror-001'
        mirrorService.getMirrorResult(id) >> makeResult(id)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/mirrors/${id}"), ContainerMirrorResponse)

        then:
        resp.status == HttpStatus.OK
        resp.body().mirrorId     == id
        resp.body().digest       == 'sha256:abc123'
        resp.body().sourceImage  == 'docker.io/library/ubuntu:22.04'
        resp.body().targetImage  == 'example.io/mirror/ubuntu:22.04'
        resp.body().status       == Status.COMPLETED
        resp.body().exitCode     == 0
    }

    // ----------------------------------------------------------------
    // GET /w1/mirrors/{id} – not found
    // ----------------------------------------------------------------
    def 'GET /w1/mirrors/{id} returns 404 when record not found'() {
        given:
        mirrorService.getMirrorResult('missing') >> null

        when:
        client.toBlocking().exchange(HttpRequest.GET('/w1/mirrors/missing'), ContainerMirrorResponse)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // ----------------------------------------------------------------
    // GET /w1/mirrors/{id}/logs – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/mirrors/{id}/logs returns 200 with log text and Content-Disposition header'() {
        given:
        def id = 'mirror-002'
        def logText = 'Mirror completed successfully'
        mirrorService.getMirrorResult(id) >> makeResult(id)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/mirrors/${id}/logs"), String)

        then:
        resp.status == HttpStatus.OK
        resp.body() == logText
        resp.header('Content-Disposition') == "attachment; filename=${id}.log"
    }

    // ----------------------------------------------------------------
    // GET /w1/mirrors/{id}/logs – not found
    // ----------------------------------------------------------------
    def 'GET /w1/mirrors/{id}/logs returns 404 when record not found'() {
        given:
        mirrorService.getMirrorResult('missing') >> null

        when:
        client.toBlocking().exchange(HttpRequest.GET('/w1/mirrors/missing/logs'), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

}
