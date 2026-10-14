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

package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryAuthServiceImpl
import jakarta.inject.Inject

@MicronautTest
@Property(name = 'wave.security.ssrf-protection.enabled', value = 'true')
class ValidateCredsSsrfControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    RegistryAuthService loginService

    @MockBean(RegistryAuthServiceImpl)
    RegistryAuthService loginService() {
        Mock(RegistryAuthService)
    }

    static private Map body(String endpoint, String registry) {
        final creds = [userName: 'test', password: 'test', registry: registry]
        return endpoint == '/validate-creds' ? [request: creds] : creds
    }

    @Unroll
    void 'should reject SSRF attempts with #description'() {
        given:
        def req = [
                userName: 'test',
                password: 'test',
                registry: registry
        ]
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", req)

        when:
        client.toBlocking().exchange(request, Boolean)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.message.contains(expectedMsg)

        where:
        registry          | description        | expectedMsg
        '127.0.0.1'       | 'loopback IP'      | 'Invalid registry hostname'
        'localhost'       | 'localhost'         | 'localhost'
        '169.254.169.254' | 'AWS metadata IP'  | 'Invalid registry hostname'
        '10.0.0.1'        | 'private network'  | 'Invalid registry hostname'
    }

    @Unroll
    void 'should validate the registry of #registry on #endpoint'() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.POST(endpoint, body(endpoint, registry)), Boolean)

        then:
        1 * loginService.validateUser(expected, 'test', 'test') >> true
        and:
        response.body()

        where:
        endpoint                    | registry                              | expected
        '/validate-creds'           | 'quay.io/org/repo'                    | 'quay.io'
        '/v1alpha2/validate-creds'  | 'quay.io'                             | 'quay.io'
        '/v1alpha2/validate-creds'  | 'quay.io/org'                         | 'quay.io'
        '/v1alpha2/validate-creds'  | 'quay.io:443/org/sub_dir.x-y/repo'    | 'quay.io:443'
    }

    @Unroll
    void 'should reject repository #registry on #endpoint'() {
        when:
        client.toBlocking().exchange(HttpRequest.POST(endpoint, body(endpoint, registry)), Boolean)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        0 * loginService.validateUser(_, _, _)

        where:
        endpoint                    | registry
        '/validate-creds'           | 'quay.io/org/repo:latest'
        '/v1alpha2/validate-creds'  | 'quay.io/org/repo:latest'
        '/v1alpha2/validate-creds'  | 'quay.io/org/repo@sha256:0000'
        '/v1alpha2/validate-creds'  | 'quay.io/org/'
        '/v1alpha2/validate-creds'  | 'quay.io//org'
        '/v1alpha2/validate-creds'  | 'localhost/org'
        '/v1alpha2/validate-creds'  | 'https://quay.io'
        '/v1alpha2/validate-creds'  | 'https://quay.io/org'
    }
}
