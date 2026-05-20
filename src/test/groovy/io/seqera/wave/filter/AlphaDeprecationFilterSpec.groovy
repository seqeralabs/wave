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

package io.seqera.wave.filter

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for {@link AlphaDeprecationFilter}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class AlphaDeprecationFilterSpec extends Specification {

    @Inject
    @Client('/')
    HttpClient client

    @Unroll
    def 'response from #path carries Deprecation + Sunset headers'(String path) {
        when:
        def resp = exchangeOrCapture(path)

        then:
        resp.headers.get('Deprecation') == 'true'
        resp.headers.get('Sunset')

        where:
        path << [
            '/service-info',                  // unversioned legacy
            '/v1alpha1/builds/bd-fake',       // 404 path — filter must still stamp headers
            '/v1alpha2/container/fake',
        ]
    }

    def 'response from /w1/* path is NOT marked deprecated'() {
        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/w1/service-info'), String)

        then:
        resp.status.code == 200
        !resp.headers.contains('Deprecation')
        !resp.headers.contains('Sunset')
    }

    private exchangeOrCapture(String path) {
        try {
            return client.toBlocking().exchange(HttpRequest.GET(path), String)
        } catch (HttpClientResponseException ex) {
            return ex.response
        }
    }
}
