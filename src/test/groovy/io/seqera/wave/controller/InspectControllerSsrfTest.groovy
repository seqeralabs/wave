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
 * Verify the inspect endpoint refuses container images hosted on loopback,
 * private, link-local and metadata addresses when SSRF protection is enabled.
 *
 * These cases reproduce the SSRF reconnaissance probes reported against
 * {@code POST /v1alpha1/inspect}: with protection enabled they must fail with
 * a 400 response before any socket is opened to the target host.
 *
 * @author Oussama El Azizi <oussama.elazizi@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.security.ssrf-protection.enabled', value = 'true')
class InspectControllerSsrfTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Unroll
    def 'should reject inspect request targeting #description'() {
        given:
        def request = HttpRequest.POST("/v1alpha1/inspect", [containerImage: containerImage])

        when:
        client.toBlocking().exchange(request, ContainerInspectResponse)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        where:
        containerImage              | description
        '127.0.0.1:8080/probe:1'    | 'loopback address'
        'localhost:5000/probe:1'    | 'localhost'
        '10.0.0.1:8080/probe:1'     | 'RFC1918 private address'
        '192.168.1.1/probe:1'       | 'RFC1918 private address with default port'
        '169.254.169.254/probe:1'   | 'cloud metadata address'
    }

}
