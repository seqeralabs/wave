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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 * Verify that the /view/** pages are disabled when 'wave.views.enabled' is false.
 *
 * @author Gavin Elder
 */
@Property(name = 'wave.server.url', value = 'http://foo.com')
@Property(name = 'wave.views.enabled', value = 'false')
@MicronautTest
class ViewControllerDisabledTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    ApplicationContext applicationContext

    def 'should not load the view controller when views are disabled'() {
        expect:
        !applicationContext.containsBean(ViewController)
    }

    def 'should return not found for view pages when views are disabled'() {
        when:
        client.toBlocking().exchange(HttpRequest.GET(uri))

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND

        where:
        uri << [
                '/view/builds/bd-12345',
                '/view/mirrors/mr-12345',
                '/view/scans/sc-12345',
                '/view/containers/1234567890ab',
        ]
    }

}
