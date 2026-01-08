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
import io.micronaut.test.extensions.spock.annotation.MicronautTest
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

    void 'should NEVER expose stack traces in error responses'() {
        given: 'various types of invalid requests'
        def requests = [
                HttpRequest.POST("/v1alpha2/container", '{"invalid json').contentType("application/json"),
                HttpRequest.POST("/v1alpha2/container", '[]').contentType("application/json"),
                HttpRequest.POST("/v1alpha2/container", '{"test": invalid}').contentType("application/json"),
                HttpRequest.POST("/v1alpha2/container", '{"packages":{"type":"INVALID"}}').contentType("application/json"),
        ]

        expect: 'no stack traces in any error response'
        requests.each { request ->
            try {
                client.toBlocking().exchange(request, String)
                assert false, "Request should have failed"
            } catch (HttpClientResponseException ex) {
                def body = ex.response.getBody(String).orElse("")

                // CRITICAL: No stack traces
                assert !body.contains(' at io.'), "Stack trace with 'at io.' found"
                assert !body.contains(' at com.'), "Stack trace with 'at com.' found"
                assert !body.contains(' at java.'), "Stack trace with 'at java.' found"
                assert !body.contains('.java:'), "Stack trace with file:line found"
                assert !body.contains('.groovy:'), "Stack trace with groovy file found"
                assert !body.contains('Caused by:'), "Stack trace with 'Caused by' found"
                assert !body.contains('StackTrace'), "StackTrace term found"
                assert !body.contains('Exception in thread'), "Exception stack found"
            }
        }
    }

    void 'should NOT expose internal package names'() {
        given: 'various types of invalid requests'
        def requests = [
                HttpRequest.POST("/v1alpha2/container", '{"bad"}').contentType("application/json"),
                HttpRequest.POST("/v1alpha2/container", '{"containerFile": 999}').contentType("application/json"),
        ]

        expect: 'no internal package names exposed'
        requests.each { request ->
            try {
                client.toBlocking().exchange(request, String)
                assert false, "Request should have failed"
            } catch (HttpClientResponseException ex) {
                def body = ex.response.getBody(String).orElse("")

                // No internal package structures
                assert !body.contains('io.seqera.wave'), "Internal package io.seqera.wave exposed"
                assert !body.contains('com.fasterxml.jackson'), "Jackson package exposed"
                assert !body.contains('jakarta.validation'), "Jakarta validation package exposed"
            }
        }
    }

    void 'should NOT expose enum values'() {
        given: 'request with invalid enum value'
        def request = HttpRequest.POST("/v1alpha2/container", '''
            {
                "containerImage": "ubuntu:latest",
                "packages": {
                    "type": "INVALID_ENUM_VALUE",
                    "entries": []
                }
            }
        ''').contentType("application/json")

        when: 'the request is sent'
        client.toBlocking().exchange(request, String)

        then: 'exception is thrown'
        def ex = thrown(HttpClientResponseException)

        and: 'enum values are not exposed'
        def body = ex.response.getBody(String).get()
        !body.contains('[CONDA, SPACK')  // Enum value list
        !body.contains('BIOCONDA')
        !body.contains('PackageType')   // Class name
    }

    void 'should NOT expose Java exception class names'() {
        given: 'invalid JSON request'
        def request = HttpRequest.POST("/v1alpha2/container", '{"test"')
                .contentType("application/json")

        when: 'the request is sent'
        client.toBlocking().exchange(request, String)

        then: 'exception is thrown'
        def ex = thrown(HttpClientResponseException)

        and: 'exception class names are not exposed'
        def body = ex.response.getBody(String).get()
        !body.contains('JsonProcessingException')
        !body.contains('JsonParseException')
        !body.contains('JsonMappingException')
        !body.contains('ConstraintViolationException')
        !body.contains('IllegalArgumentException')
    }

    void 'error responses should include correlation ID'() {
        given: 'invalid JSON request'
        def request = HttpRequest.POST("/v1alpha2/container", '{"x"')
                .contentType("application/json")

        when: 'the request is sent'
        client.toBlocking().exchange(request, String)

        then: 'exception is thrown'
        def ex = thrown(HttpClientResponseException)

        and: 'response contains a correlation ID (request ID or error ID)'
        def body = ex.response.getBody(String).get()
        body.contains('requestId') || body.contains('Error ID')
        body ==~ /.*[a-f0-9]{10,}.*/  // Contains hex ID for correlation
    }
}
