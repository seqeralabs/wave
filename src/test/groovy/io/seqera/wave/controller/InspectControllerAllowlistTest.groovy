/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
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
import spock.lang.Unroll

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerInspectResponse
import jakarta.inject.Inject
/**
 * Verify anonymous inspect requests are restricted to the configured registry
 * allowlist when SSRF protection is enabled, and rejected before any socket is
 * opened to a non allow-listed registry.
 *
 * @author Oussama El Azizi <oussama.elazizi@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.security.ssrf-protection.enabled', value = 'true')
@Property(name = 'wave.security.ssrf-protection.registry-allowlist', value = 'docker.io,quay.io')
class InspectControllerAllowlistTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Unroll
    def 'anonymous inspect of #containerImage should be rejected by the allowlist'() {
        given:
        def request = HttpRequest.POST("/v1alpha1/inspect", [containerImage: containerImage])

        when:
        client.toBlocking().exchange(request, ContainerInspectResponse)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.message.contains('is not allowed for anonymous requests')

        where:
        containerImage << [
                'ghcr.io/foo/bar:1',
                'public.ecr.aws/foo/bar:1',
                'kubernetes.default.svc/probe:1',
                // the allowlist is applied before host resolution, so rebinding
                // hosts are refused as well
                '169.254.169.254.nip.io/probe:1' ]
    }

}
