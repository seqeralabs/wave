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

    void 'should sanitize error messages and not expose internal class names'() {
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

        and: 'the error message does not contain internal class names'
        !error.message.contains('io.seqera.wave')
        !error.message.contains('PackagesSpec')
        !error.message.contains('through reference chain')
        !error.message.contains('at [Source:')

        and: 'the error message contains an error ID'
        error.message.contains('Error ID:')
    }

    void 'should sanitize error with XSS attempt in value'() {
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

        and: 'the error message does not expose internal implementation details'
        !error.message.contains('io.seqera.wave.api.PackagesSpec')
        !error.message.contains('through reference chain')
        !error.message.contains('Cannot deserialize value of type')

        and: 'the error message is user-friendly'
        error.message.contains('Error ID:')
    }

    void 'should handle invalid JSON and not expose internal details'() {
        given: 'a request with malformed JSON'
        def request = HttpRequest
                .POST("/v1alpha2/container", '{"packages": {"type": }')
                .contentType(MediaType.APPLICATION_JSON_TYPE)

        when: 'submitting the request'
        client.toBlocking().exchange(request, JsonError)

        then: 'an exception is thrown'
        def exception = thrown(HttpClientResponseException)
        def error = exception.response.getBody(JsonError).get()

        and: 'the error message does not contain internal class paths'
        !error.message.contains('io.seqera')
        !error.message.contains('com.fasterxml.jackson')

        and: 'error has an ID for tracking'
        error.message.contains('Error ID:')
    }

    def 'should sanitize error message with due to prefix'() {
        given: 'an error message with "due to:" prefix that exposes internal details'
        def unsanitized = 'Failed to convert argument [packages] for value [INVALID] due to: Cannot deserialize value of type `io.seqera.wave.api.PackagesSpec$Type` from String "INVALID"'

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'internal class names and prefixes are removed'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"

        and: 'the sanitized message does not contain internal details'
        !sanitized.contains('Failed to convert argument')
        !sanitized.contains('io.seqera.wave')
        !sanitized.contains('PackagesSpec')
        !sanitized.contains('due to:')

        and: 'the sanitized message is user-friendly'
        sanitized == 'Invalid value "INVALID"'
    }

    def 'should sanitize error message with Jackson source location'() {
        given: 'an error message with Jackson source location that exposes internals'
        def unsanitized = 'Cannot deserialize value of type `io.seqera.wave.api.PackagesSpec$Type`\n at [Source: (String)"{"packages":{"type":"INVALID"}}"; line: 1, column: 24]'

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'source location and class names are removed'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"

        and: 'the sanitized message does not contain source location'
        !sanitized.contains('at [Source:')
        !sanitized.contains('line:')
        !sanitized.contains('column:')
        !sanitized.contains('io.seqera.wave')

        and: 'the sanitized message is clean'
        sanitized == 'Cannot deserialize value of type the specified type'
    }

    def 'should sanitize error message with reference chain'() {
        given: 'an error message with reference chain that exposes internal structure'
        def unsanitized = 'Invalid type (through reference chain: io.seqera.wave.api.ContainerRequest["packages"]->io.seqera.wave.api.PackagesSpec["type"])'

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'reference chain is removed'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"

        and: 'the sanitized message does not contain reference chain'
        !sanitized.contains('through reference chain')
        !sanitized.contains('io.seqera.wave')
        !sanitized.contains('ContainerRequest')
        !sanitized.contains('PackagesSpec')

        and: 'the sanitized message is simplified'
        sanitized == 'Invalid type'
    }

    def 'should sanitize error message with backtick-wrapped class names'() {
        given: 'an error message with backtick-wrapped class names'
        def unsanitized = 'Cannot construct instance of `io.seqera.wave.api.PackagesSpec$Type` from String value "INVALID"'

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'class names are replaced with generic text'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"

        and: 'the sanitized message does not contain class names'
        !sanitized.contains('io.seqera.wave')
        !sanitized.contains('PackagesSpec')
        !sanitized.contains('`')

        and: 'the sanitized message uses generic replacement'
        sanitized.contains('the specified type')
    }

    def 'should sanitize error message with unquoted fully qualified class names'() {
        given: 'an error message with unquoted fully qualified class names'
        def unsanitized = 'Type mismatch: io.seqera.wave.api.PackagesSpec expected but got java.lang.String'

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'fully qualified class names are replaced'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"

        and: 'the sanitized message does not contain package paths'
        !sanitized.contains('io.seqera.wave')
        !sanitized.contains('java.lang.String')

        and: 'the sanitized message uses generic replacement'
        sanitized == 'Type mismatch: the specified type expected but got the specified type'
    }

    def 'should sanitize complex error message with multiple patterns'() {
        given: 'a complex error message with multiple internal details'
        def unsanitized = '''Failed to convert argument [request] for value [null] due to: Cannot deserialize value of type `io.seqera.wave.api.ContainerRequest` from String "invalid"
 at [Source: (String)"invalid"; line: 1, column: 1] (through reference chain: io.seqera.wave.api.ContainerRequest["packages"]->io.seqera.wave.api.PackagesSpec["type"])'''

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'all internal details are removed'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"

        and: 'the sanitized message is clean and user-friendly'
        !sanitized.contains('Failed to convert argument')
        !sanitized.contains('due to:')
        !sanitized.contains('at [Source:')
        !sanitized.contains('through reference chain')
        !sanitized.contains('io.seqera.wave')
        !sanitized.contains('`')

        and: 'multiple spaces are cleaned up'
        !sanitized.contains('  ')
    }

    def 'should handle null and empty messages'() {
        when: 'sanitizing null message'
        def sanitizedNull = ErrorHandler.sanitizeErrorMessage(null)

        then: 'returns default message'
        println "UNSANITIZED: null"
        println "SANITIZED: $sanitizedNull"
        sanitizedNull == 'Invalid request'

        when: 'sanitizing empty message'
        def sanitizedEmpty = ErrorHandler.sanitizeErrorMessage('')

        then: 'returns default message'
        println "UNSANITIZED: (empty string)"
        println "SANITIZED: $sanitizedEmpty"
        sanitizedEmpty == 'Invalid request'
    }

    def 'should sanitize Cannot deserialize messages'() {
        given: 'an error message with "Cannot deserialize value of type" text'
        def unsanitized = 'Cannot deserialize value of type io.seqera.wave.api.PackagesSpec from String "INVALID"'

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'the message is simplified'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"

        and: 'the sanitized message is user-friendly'
        sanitized == 'Invalid value "INVALID"'
    }

    def 'should preserve user-friendly error messages'() {
        given: 'a user-friendly error message without internal details'
        def unsanitized = 'Invalid request: missing required field'

        when: 'the error message is sanitized'
        def sanitized = ErrorHandler.sanitizeErrorMessage(unsanitized)

        then: 'the message remains unchanged'
        println "UNSANITIZED: $unsanitized"
        println "SANITIZED: $sanitized"
        sanitized == unsanitized
    }
}
