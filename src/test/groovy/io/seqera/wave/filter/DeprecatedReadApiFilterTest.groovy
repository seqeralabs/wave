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

import spock.lang.Specification

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class DeprecatedReadApiFilterTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    /** the status is irrelevant: the filter runs on whatever the route answers */
    private headersOf(HttpRequest request) {
        try {
            return client.toBlocking().exchange(request, String).headers
        }
        catch (HttpClientResponseException e) {
            return e.response.headers
        }
    }

    def 'should mark the superseded read routes' () {
        when:
        def headers = headersOf(HttpRequest.GET(ROUTE))

        then:
        headers.get('Deprecation') == 'true'
        headers.get('Link') == '</v1alpha3>; rel="successor-version"'

        where:
        ROUTE << [
                '/v1alpha1/builds/bd-unknown',
                '/v1alpha1/builds/bd-unknown/status',
                '/v1alpha1/builds/bd-unknown/logs',
                '/v1alpha1/builds/bd-unknown/condalock',
                '/v1alpha1/mirrors/mr-unknown',
                '/v1alpha1/mirrors/mr-unknown/logs',
                '/v1alpha1/scans/sc-unknown',
                '/v1alpha1/scans/sc-unknown/spdx',
                '/v1alpha1/scans/sc-unknown/logs',
                '/v1alpha2/container/unknown',
                '/v1alpha2/container/unknown/status',
                '/container-token/unknown',
        ]
    }

    def 'should not mark a submit, because the successor API has no submit route' () {
        when: 'the submit routes sit under the same prefixes'
        def headers = headersOf(REQUEST)

        then:
        headers.get('Deprecation') == null
        headers.get('Link') == null

        where:
        REQUEST << [
                HttpRequest.POST('/container-token', [:]),
                HttpRequest.POST('/v1alpha2/container', [:]),
                // security refuses this one before the route runs, so it never reaches the
                // filter: kept for the end state, it does not exercise the method restriction
                HttpRequest.DELETE('/container-token/unknown'),
        ]
    }

    def 'should not mark the successor API itself' () {
        when:
        def headers = headersOf(HttpRequest.GET('/v1alpha3/builds/bd-unknown'))

        then:
        headers.get('Deprecation') == null
    }

}
