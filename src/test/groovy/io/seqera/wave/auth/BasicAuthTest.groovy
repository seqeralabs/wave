/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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
package io.seqera.wave.auth

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import static io.micronaut.http.HttpStatus.OK
import static io.micronaut.http.HttpStatus.UNAUTHORIZED
import static io.micronaut.http.MediaType.TEXT_PLAIN

/**
 * test for basic authentication
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name="wave.auth.basic.username",value="username")
@Property(name="wave.auth.basic.password",value="password")
class BasicAuthTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "by default every endpoint is secured"() {
        when: 'Accessing a secured URL without authenticating'
        client.toBlocking().exchange(HttpRequest.GET('/').accept(TEXT_PLAIN))

        then: 'returns unauthorized'
        HttpClientResponseException e = thrown()
        e.status == UNAUTHORIZED
        e.response.headers.contains("WWW-Authenticate")
        e.response.headers.get("WWW-Authenticate") == 'Basic realm="Wave Authentication"'
    }

    void "Verify HTTP Basic Auth works"() {
        when: 'A secured URL is accessed with Basic Auth'
        HttpRequest request = HttpRequest.GET('/metrics')
                .basicAuth("username", "password")
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then: 'the endpoint can be accessed'
        rsp.status == OK
    }
}
