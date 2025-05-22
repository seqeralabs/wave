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

package io.seqera.wave.filter

import spock.lang.Specification

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class DenyCrawlerFilterTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    def 'should allow robots.txt' () {
        when:
        def request = HttpRequest.GET("/robots.txt").header("User-Agent", "Googlebot")
        def resp = client.toBlocking().exchange(request, String)
        then:
        resp.status.code == 200
    }

    def 'should disallow anything else' () {
        when:
        def request = HttpRequest.GET("/service-info").header("User-Agent", "Googlebot")
        client.toBlocking().exchange(request, String)
        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 405
    }

}
