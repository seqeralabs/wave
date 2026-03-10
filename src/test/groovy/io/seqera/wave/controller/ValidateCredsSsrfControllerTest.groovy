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

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
@Property(name = 'wave.security.ssrf-protection.enabled', value = 'true')
class ValidateCredsSsrfControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void 'should reject SSRF attempts with private IP'() {
        given:
        def req = [
                userName: 'test',
                password: 'test',
                registry: '127.0.0.1'
        ]
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", req)

        when:
        client.toBlocking().exchange(request, Boolean)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.message.contains('loopback')
    }

    void 'should reject SSRF attempts with localhost'() {
        given:
        def req = [
                userName: 'test',
                password: 'test',
                registry: 'localhost'
        ]
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", req)

        when:
        client.toBlocking().exchange(request, Boolean)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.message.contains('localhost')
    }

    void 'should reject SSRF attempts with AWS metadata IP'() {
        given:
        def req = [
                userName: 'test',
                password: 'test',
                registry: '169.254.169.254'
        ]
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", req)

        when:
        client.toBlocking().exchange(request, Boolean)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.message.contains('metadata')
    }

    void 'should reject SSRF attempts with private network IP'() {
        given:
        def req = [
                userName: 'test',
                password: 'test',
                registry: '10.0.0.1'
        ]
        HttpRequest request = HttpRequest.POST("/v1alpha2/validate-creds", req)

        when:
        client.toBlocking().exchange(request, Boolean)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.message.contains('private')
    }
}