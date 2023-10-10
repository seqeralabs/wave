/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
    HttpClient client;

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
}
