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

package io.seqera.wave.util

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.test.extensions.spock.annotation.MicronautTest

/**
 * Tests for SecureHttpClientAddressResolver
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class SecureHttpClientAddressResolverTest extends Specification {

    def 'should load SecureHttpClientAddressResolver when ALB profile is enabled'() {
        given: 'context with ALB profile'
        def ctx = ApplicationContext.run([
                'micronaut.server.host-resolution.client-address-header': 'X-Forwarded-For'
        ])

        when: 'retrieving the HttpClientAddressResolver bean'
        def resolver = ctx.getBean(HttpClientAddressResolver)

        then: 'it should be our secure implementation'
        resolver instanceof SecureHttpClientAddressResolver

        cleanup:
        ctx.close()
    }

    def 'should use default resolver when ALB profile is not enabled'() {
        given: 'context without ALB profile'
        def ctx = ApplicationContext.run([:])

        when: 'retrieving the HttpClientAddressResolver bean'
        def resolver = ctx.getBean(HttpClientAddressResolver)

        then: 'it should NOT be our secure implementation'
        !(resolver instanceof SecureHttpClientAddressResolver)

        cleanup:
        ctx.close()
    }

    def 'should extract rightmost IP from comma-separated X-Forwarded-For'() {
        given: 'a SecureHttpClientAddressResolver'
        def ctx = ApplicationContext.run([
                'micronaut.server.host-resolution.client-address-header': 'X-Forwarded-For'
        ])
        def resolver = ctx.getBean(SecureHttpClientAddressResolver)

        and: 'a mock request with comma-separated X-Forwarded-For header'
        HttpHeaders headers = Mock(HttpHeaders) {
            get('X-Forwarded-For') >> headerValue
        }
        HttpRequest request = Mock(HttpRequest) {
            getHeaders() >> headers
        }

        when: 'resolving the client address'
        def result = resolver.resolve(request)

        then: 'the rightmost IP is returned'
        result == expected

        cleanup:
        ctx.close()

        where:
        headerValue                                     | expected
        '10.0.0.1, 192.168.1.99, 203.0.113.42'         | '203.0.113.42'
        'spoofed-ip, real-client-ip'                    | 'real-client-ip'
        '10.0.0.1,  172.16.0.1 '                       | '172.16.0.1'
    }

    def 'should return single IP from X-Forwarded-For without comma'() {
        given: 'a SecureHttpClientAddressResolver'
        def ctx = ApplicationContext.run([
                'micronaut.server.host-resolution.client-address-header': 'X-Forwarded-For'
        ])
        def resolver = ctx.getBean(SecureHttpClientAddressResolver)

        and: 'a mock request with a single IP in X-Forwarded-For'
        HttpHeaders headers = Mock(HttpHeaders) {
            get('X-Forwarded-For') >> '203.0.113.42'
        }
        HttpRequest request = Mock(HttpRequest) {
            getHeaders() >> headers
        }

        when: 'resolving the client address'
        def result = resolver.resolve(request)

        then: 'the single IP is returned'
        result == '203.0.113.42'

        cleanup:
        ctx.close()
    }
}