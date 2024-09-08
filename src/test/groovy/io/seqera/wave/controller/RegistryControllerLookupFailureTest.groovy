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
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.exception.RegistryForwardException
import io.seqera.wave.model.ContentType
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryControllerLookupFailureTest extends Specification implements DockerRegistryContainer {

    @Inject
    @Client("/")
    HttpClient client

    @MockBean(RegistryAuthService)
    RegistryAuthService lookupService = Mock(RegistryAuthService)

    def setupSpec() {
        initRegistryContainer()
    }

    def 'should report registry lookup' () {
        when:
        def MESSAGE = 'Registry response body here'
        def HEADERS = ['x-foo': ['this'], 'x-bar': ['that']]
        lookupService.lookup(_) >> { throw new RegistryForwardException('Oops.. something went wrong', 400, MESSAGE, HEADERS) }
        and:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6").headers({ h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        client.toBlocking().exchange(request,String)
        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 400
        e.response.body() == MESSAGE
        e.response.headers.get('x-foo') == 'this'
        e.response.headers.get('x-bar') == 'that'
    }

}
