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

package io.seqera.wave.controller

import spock.lang.Specification

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ServiceInfoControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    EmbeddedServer embeddedServer;

    def 'should get service info' () {
        when:
        def request = HttpRequest.GET("/service-info")
        def resp = client.toBlocking().exchange(request, String)
        then:
        resp.status.code == 200
    }

    def 'should deny service info' () {
        when:
        def request = HttpRequest.GET("/service-info").header('User-Agent','Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)')
        client.toBlocking().exchange(request, String)
        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.METHOD_NOT_ALLOWED
    }

    def 'should redirect to /openapi/'() {
        given:
        def uri = embeddedServer.getContextURI()
        and:
        // Create a new HttpClient with redirects disabled
        def config = new DefaultHttpClientConfiguration()
        config.setFollowRedirects(false)
        def client = HttpClient.create(uri.toURL(), config)
        when:
        def request = HttpRequest.GET("/openapi")
        def resp = client.toBlocking().exchange(request, String)

        then:
        resp.status == HttpStatus.MOVED_PERMANENTLY // Expect 301
        resp.headers.get("Location") == "/openapi/" // Validate redirect location
    }

    def 'should get favicon' () {
        when:
        def request = HttpRequest.GET("/favicon.ico")
        def resp = client.toBlocking().exchange(request, String)
        then:
        resp.status.code == 200
    }

    def 'should get robots' () {
        when:
        def request = HttpRequest.GET("/robots.txt")
        def resp = client.toBlocking().exchange(request, String)
        then:
        resp.status.code == 200
    }
}
