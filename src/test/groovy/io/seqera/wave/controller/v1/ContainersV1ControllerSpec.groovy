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
import io.seqera.wave.api.ContainerStatus as InternalContainerStatus
import io.seqera.wave.api.ContainerStatusResponse as InternalContainerStatusResponse
import io.seqera.wave.api.SubmitContainerTokenResponse as InternalSubmitResponse
import io.seqera.wave.api.v1.model.ContainerRequest
import io.seqera.wave.api.v1.model.ContainerResponse
import io.seqera.wave.api.v1.model.ContainerStatus
import io.seqera.wave.api.v1.model.ContainerStatusResponse
import io.seqera.wave.api.v1.model.WaveContainerRecord
import io.seqera.wave.controller.ContainerRequestHandler
import io.seqera.wave.service.persistence.WaveContainerRecord as InternalWaveContainerRecord
import io.seqera.wave.tower.User as InternalUser
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Spock spec for {@link ContainersV1Controller} – covers all four /w1/containers/* routes.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainersV1ControllerSpec extends Specification {

    @Inject @Client('/') HttpClient client
    @Inject ContainerRequestHandler handler

    @MockBean(ContainerRequestHandler)
    ContainerRequestHandler mockHandler() { Mock(ContainerRequestHandler) }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InternalWaveContainerRecord makeInternalRecord(String requestId) {
        // WaveContainerRecord is @Canonical with final fields; use Stub to avoid
        // needing the complex primary constructor
        def record = Stub(InternalWaveContainerRecord) {
            getId() >> requestId
            getUser() >> new InternalUser(id: 1L, userName: 'tester', email: 'tester@example.com')
            getWorkspaceId() >> 42L
            getContainerImage() >> 'docker.io/library/ubuntu:22.04'
            getContainerFile() >> null
            getCondaFile() >> null
            getPlatform() >> 'linux/amd64'
            getTowerEndpoint() >> 'https://api.cloud.seqera.io'
            getBuildRepository() >> null
            getCacheRepository() >> null
            getFingerprint() >> 'abc123'
            getTimestamp() >> Instant.parse('2025-01-01T00:00:00Z')
            getExpiration() >> Instant.parse('2025-01-02T00:00:00Z')
            getZoneId() >> '+00:00'
            getIpAddress() >> '1.2.3.4'
            getSourceImage() >> 'docker.io/library/ubuntu:22.04'
            getSourceDigest() >> 'sha256:abcdef'
            getWaveImage() >> 'wave.seqera.io/ubuntu:22.04'
            getWaveDigest() >> null
            getBuildId() >> null
            getBuildNew() >> null
            getFreeze() >> false
            getFusionVersion() >> null
            getMirror() >> false
            getScanId() >> null
        }
        return record
    }

    private static InternalSubmitResponse makeInternalResponse(String requestId) {
        return new InternalSubmitResponse(
            requestId,
            'token-abc',
            'wave.seqera.io/ubuntu:22.04',
            Instant.parse('2025-01-02T00:00:00Z'),
            'docker.io/library/ubuntu:22.04',
            null,
            null,
            false,
            false,
            null,
            true
        )
    }

    private static InternalContainerStatusResponse makeInternalStatus(String requestId) {
        return new InternalContainerStatusResponse(
            requestId,
            InternalContainerStatus.DONE,
            null,
            null,
            null,
            null,
            true,
            null,
            null,
            Instant.parse('2025-01-01T00:00:00Z'),
            Duration.ofSeconds(10)
        )
    }

    // -------------------------------------------------------------------------
    // POST /w1/containers – happy path
    // -------------------------------------------------------------------------
    def 'POST /w1/containers returns 200 with mapped ContainerResponse'() {
        given:
        def requestId = 'req-001'
        // containerPlatform and format are @NotNull in the v1 spec
        def req = new ContainerRequest()
                .containerImage('docker.io/library/ubuntu:22.04')
                .containerPlatform('linux/amd64')
                .format(io.seqera.wave.api.v1.model.ContainerRequestFormat.DOCKER)

        when:
        def resp = client.toBlocking().exchange(
            HttpRequest.POST('/w1/containers', req),
            ContainerResponse
        )

        then:
        1 * handler.submit(_, _) >> makeInternalResponse(requestId)
        resp.status == HttpStatus.OK
        resp.body().requestId == requestId
        resp.body().containerToken == 'token-abc'
        resp.body().targetImage == 'wave.seqera.io/ubuntu:22.04'
        resp.body().containerImage == 'docker.io/library/ubuntu:22.04'
        resp.body().status == ContainerStatus.DONE
    }

    // -------------------------------------------------------------------------
    // GET /w1/containers/{id} – happy path
    // -------------------------------------------------------------------------
    def 'GET /w1/containers/{id} returns 200 with mapped WaveContainerRecord'() {
        given:
        def id = 'req-002'

        when:
        def resp = client.toBlocking().exchange(
            HttpRequest.GET("/w1/containers/${id}"),
            WaveContainerRecord
        )

        then:
        1 * handler.findRecord(id) >> makeInternalRecord(id)
        resp.status == HttpStatus.OK
        resp.body().user.userName == 'tester'
        resp.body().user.email == 'tester@example.com'
        resp.body().workspaceId == 42L
        resp.body().containerImage == 'docker.io/library/ubuntu:22.04'
        resp.body().platform == 'linux/amd64'
        resp.body().waveImage == 'wave.seqera.io/ubuntu:22.04'
    }

    // -------------------------------------------------------------------------
    // GET /w1/containers/{id} – not found
    // -------------------------------------------------------------------------
    def 'GET /w1/containers/{id} returns 404 when record not found'() {
        given:
        def id = 'missing'

        when:
        client.toBlocking().exchange(HttpRequest.GET("/w1/containers/${id}"), WaveContainerRecord)

        then:
        1 * handler.findRecord(id) >> null
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // -------------------------------------------------------------------------
    // GET /w1/containers/{id}/status – happy path
    // -------------------------------------------------------------------------
    def 'GET /w1/containers/{id}/status returns 200 with mapped ContainerStatusResponse'() {
        given:
        def id = 'req-003'

        when:
        def resp = client.toBlocking().exchange(
            HttpRequest.GET("/w1/containers/${id}/status"),
            ContainerStatusResponse
        )

        then:
        1 * handler.findStatus(id) >> makeInternalStatus(id)
        resp.status == HttpStatus.OK
        resp.body().id == id
        resp.body().status == ContainerStatus.DONE
        resp.body().succeeded == true
    }

    // -------------------------------------------------------------------------
    // GET /w1/containers/{id}/status – not found
    // -------------------------------------------------------------------------
    def 'GET /w1/containers/{id}/status returns 404 when status not found'() {
        given:
        def id = 'missing-status'

        when:
        client.toBlocking().exchange(HttpRequest.GET("/w1/containers/${id}/status"), ContainerStatusResponse)

        then:
        1 * handler.findStatus(id) >> null
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // -------------------------------------------------------------------------
    // DELETE /w1/containers/{id} – happy path (204)
    // -------------------------------------------------------------------------
    def 'DELETE /w1/containers/{id} returns 204 when revoke succeeds'() {
        given:
        def id = 'req-004'

        when:
        def resp = client.toBlocking().exchange(
            HttpRequest.DELETE("/w1/containers/${id}"),
            Void
        )

        then:
        1 * handler.revoke(id) >> true
        resp.status == HttpStatus.NO_CONTENT
    }

    // -------------------------------------------------------------------------
    // DELETE /w1/containers/{id} – not found (404)
    // -------------------------------------------------------------------------
    def 'DELETE /w1/containers/{id} returns 404 when revoke returns false'() {
        given:
        def id = 'missing-token'

        when:
        client.toBlocking().exchange(HttpRequest.DELETE("/w1/containers/${id}"), Void)

        then:
        1 * handler.revoke(id) >> false
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

}
