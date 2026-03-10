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

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.ErrorHandler
import io.seqera.wave.exchange.RegistryErrorResponse
import io.seqera.wave.model.ContentType
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ErrorHandlingTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void 'should handle an error'() {
        when:
        HttpRequest request = HttpRequest.GET("/v2/quay.io/hello-world/manifests/latest").headers({ h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<RegistryErrorResponse> response = client.toBlocking().exchange(request,RegistryErrorResponse)
        then:
        final exception = thrown(HttpClientResponseException)
        RegistryErrorResponse error = exception.response.getBody(RegistryErrorResponse).get()
        error.errors.get(0).message == "repository 'quay.io/hello-world:latest' not found"
    }

    void 'should not expose internal class names for invalid enum value'() {
        given: 'a request with invalid enum value'
        def request = HttpRequest
                .POST("/v1alpha2/container", [
                        packages: [type: "INVALID_TYPE"],
                        format: "docker",
                        containerPlatform: "linux/amd64"
                ])
                .contentType(MediaType.APPLICATION_JSON_TYPE)

        when: 'submitting the request'
        client.toBlocking().exchange(request, JsonError)

        then: 'an exception is thrown'
        def exception = thrown(HttpClientResponseException)
        def error = exception.response.getBody(JsonError).get()

        and: 'the error message contains an error ID'
        error.message.contains('Error ID:')

        and: 'no internal details are leaked'
        !error.message.contains('io.seqera.wave')
        !error.message.contains('PackagesSpec')
        !error.message.contains('through reference chain')
        !error.message.contains('at [Source:')
    }

    void 'should not expose internal details for XSS attempt in value'() {
        given: 'a request with XSS payload in enum value'
        def request = HttpRequest
                .POST("/v1alpha2/container", [
                        packages: [
                                type: "CRANxkhg3<script>alert(1)</script>pentest",
                                entries: ["dplyr"]
                        ],
                        format: "docker",
                        containerPlatform: "linux/amd64"
                ])
                .contentType(MediaType.APPLICATION_JSON_TYPE)

        when: 'submitting the request'
        client.toBlocking().exchange(request, JsonError)

        then: 'an exception is thrown'
        def exception = thrown(HttpClientResponseException)
        def error = exception.response.getBody(JsonError).get()

        and: 'the error message contains an error ID'
        error.message.contains('Error ID:')

        and: 'no internal details or user input are reflected'
        !error.message.contains('io.seqera.wave')
        !error.message.contains('PackagesSpec')
        !error.message.contains('<script>')
    }

    void 'should not expose internal details for invalid JSON'() {
        given: 'a request with malformed JSON'
        def request = HttpRequest
                .POST("/v1alpha2/container", '{"packages": {"type": }')
                .contentType(MediaType.APPLICATION_JSON_TYPE)

        when: 'submitting the request'
        client.toBlocking().exchange(request, JsonError)

        then: 'an exception is thrown'
        def exception = thrown(HttpClientResponseException)
        def error = exception.response.getBody(JsonError).get()

        and: 'the error message contains an error ID'
        error.message.contains('Error ID:')

        and: 'no internal details are leaked'
        !error.message.contains('io.seqera')
        !error.message.contains('com.fasterxml.jackson')
    }

    def 'sanitizeErrorMessage should handle edge cases'() {
        expect:
        ErrorHandler.sanitizeErrorMessage(input) == expected

        where:
        input                                                                           | expected
        null                                                                            | 'Unexpected error'
        ''                                                                              | 'Unexpected error'
        '   '                                                                           | 'Unexpected error'
        'Invalid request: missing required field'                                       | 'Invalid request: missing required field'
        'Cannot deserialize value of type `io.seqera.wave.api.PackagesSpec\$Type`'      | 'Cannot deserialize value of type'
        'Error with io.seqera.wave.Foo and java.lang.String in it'                      | 'Error with and in it'
        'Something from String "malicious<script>": not valid'                          | 'Something not valid'
        'payload <script>alert(1)</script> end'                                         | 'payload alert(1) end'
        'xss <img onerror="alert(1)" src=x> test'                                      | 'xss test'
    }
}
