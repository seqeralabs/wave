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
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class MirrorControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject PersistenceService persistenceService

    def "should return mirror record when service is available"() {
        given:
        def mirrorRequest = MirrorRequest.create(
                "docker.io/library/nginx:1.24-alpine",
                "wave.seqera.io/mirror/nginx:1.24-alpine",
                "sha256:abcd1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcd",
                ContainerPlatform.DEFAULT,
                Path.of("/tmp/workdir"),
                "{auth:json}",
                "scan-123",
                Instant.now(),
                "offset-12345",
                PlatformId.NULL
        );
        def mirrorResult = MirrorResult.of(mirrorRequest)
        persistenceService.saveMirrorResultAsync(mirrorResult)

        when:
        def req = HttpRequest.GET("/v1alpha1/mirrors/${mirrorRequest.mirrorId}")
        def res = client.toBlocking().exchange(req, MirrorResult)

        then:
        res.status.code == 200
        res.body() == mirrorResult
    }

    def "should return 404 when mirror record is not found"() {
        given:
        def mirrorId = "mirror123"

        when:
        def req = HttpRequest.GET("/v1alpha1/mirrors/${mirrorId}")
        client.toBlocking().exchange(req, MirrorResult)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 404
    }

    def "should return mirror logs"() {
        given:
        def mirrorId = "mirror123"
        def mirrorRequest = MirrorRequest.create(
                "docker.io/library/nginx:1.24-alpine",
                "wave.seqera.io/mirror/nginx:1.24-alpine",
                "sha256:abcd1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcd",
                ContainerPlatform.DEFAULT,
                Path.of("/tmp/workdir"),
                "{auth:json}",
                "scan-123",
                Instant.now(),
                "offset-12345",
                PlatformId.NULL
        );
        def mirrorResult = MirrorResult.of(mirrorRequest)
        mirrorResult = mirrorResult.complete(1, "Mirroring failed")
        persistenceService.saveMirrorResultAsync(mirrorResult)

        when:
        def req = HttpRequest.GET("/v1alpha1/mirrors/${mirrorRequest.mirrorId}/logs")
        def res = client.toBlocking().exchange(req, String)

        then:
        res.status.code == 200
        res.header("Content-Disposition") == "attachment; filename=${mirrorRequest.mirrorId}.log"

        and:
        res.body() == "Mirroring failed"
    }
}
