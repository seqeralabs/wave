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

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.SecurityHeadersConfig
import jakarta.inject.Inject

/**
 * Tests for SecurityHeadersFilter
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class SecurityHeadersFilterTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    SecurityHeadersConfig config

    @Inject
    SecurityHeadersFilter filter

    def 'should have correct filter order' () {
        expect:
        filter.getOrder() == FilterOrder.SECURITY_HEADERS
        and:
        FilterOrder.SECURITY_HEADERS == -120
    }

    def 'should add all security headers to response' () {
        when:
        def request = HttpRequest.GET("/service-info")
        def response = client.toBlocking().exchange(request, String)

        then:
        response.status.code == 200
        and:
        response.headers.get('Strict-Transport-Security') == 'max-age=31536000; includeSubDomains'
        response.headers.get('X-Frame-Options') == 'DENY'
        response.headers.get('X-Content-Type-Options') == 'nosniff'
        response.headers.get('Referrer-Policy') == 'strict-origin-when-cross-origin'
        response.headers.get('Permissions-Policy') == 'camera=(), microphone=(), geolocation=()'
        response.headers.get('Content-Security-Policy') == "default-src 'self'; frame-ancestors 'none'"
    }

    def 'should add security headers to all endpoints' () {
        when:
        def request = HttpRequest.GET(endpoint)
        def response = client.toBlocking().exchange(request, String)

        then:
        response.status.code in [200, 404]
        and:
        response.headers.get('Strict-Transport-Security') != null
        response.headers.get('X-Frame-Options') != null
        response.headers.get('X-Content-Type-Options') != null

        where:
        endpoint << ['/service-info', '/v2/']
    }

    def 'should not add security headers when disabled' () {
        given:
        def ctx = ApplicationContext.run([
                'wave.security.headers.enabled': 'false',
                'micronaut.server.port': -1
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def testClient = ctx.createBean(HttpClient, server.URL)

        when:
        def request = HttpRequest.GET("/service-info")
        def response = testClient.toBlocking().exchange(request, String)

        then:
        response.status.code == 200
        and:
        // Headers should not be present when filter is disabled
        response.headers.get('Strict-Transport-Security') == null
        response.headers.get('X-Frame-Options') == null
        response.headers.get('X-Content-Type-Options') == null

        cleanup:
        testClient?.close()
        server?.stop()
        ctx?.close()
    }

    def 'should use custom header values from configuration' () {
        given:
        def ctx = ApplicationContext.run([
                'wave.security.headers.enabled': 'true',
                'wave.security.headers.frame-options': 'SAMEORIGIN',
                'wave.security.headers.referrer-policy': 'no-referrer',
                'wave.security.headers.hsts.max-age': '3600',
                'wave.security.headers.hsts.include-sub-domains': 'false',
                'micronaut.server.port': -1
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def testClient = ctx.createBean(HttpClient, server.URL)

        when:
        def request = HttpRequest.GET("/service-info")
        def response = testClient.toBlocking().exchange(request, String)

        then:
        response.status.code == 200
        and:
        response.headers.get('X-Frame-Options') == 'SAMEORIGIN'
        response.headers.get('Referrer-Policy') == 'no-referrer'
        response.headers.get('Strict-Transport-Security') == 'max-age=3600'

        cleanup:
        testClient?.close()
        server?.stop()
        ctx?.close()
    }

    def 'should handle null header values gracefully' () {
        given:
        def ctx = ApplicationContext.run([
                'wave.security.headers.enabled': 'true',
                'wave.security.headers.frame-options': '',
                'wave.security.headers.permissions-policy': '',
                'micronaut.server.port': -1
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def testClient = ctx.createBean(HttpClient, server.URL)

        when:
        def request = HttpRequest.GET("/service-info")
        def response = testClient.toBlocking().exchange(request, String)

        then:
        response.status.code == 200
        and:
        // Empty configured headers should not be present
        response.headers.get('X-Frame-Options') == null || response.headers.get('X-Frame-Options') == ''
        response.headers.get('Permissions-Policy') == null || response.headers.get('Permissions-Policy') == ''
        // Other headers should still be present
        response.headers.get('X-Content-Type-Options') != null

        cleanup:
        testClient?.close()
        server?.stop()
        ctx?.close()
    }

    def 'should build HSTS header value correctly' () {
        given:
        def testConfig = new SecurityHeadersConfig()

        when:
        testConfig.hstsMaxAge = maxAge
        testConfig.hstsIncludeSubDomains = includeSubDomains
        def result = testConfig.getHstsValue()

        then:
        result == expected

        where:
        maxAge      | includeSubDomains | expected
        31536000L   | true              | 'max-age=31536000; includeSubDomains'
        31536000L   | false             | 'max-age=31536000'
        3600L       | true              | 'max-age=3600; includeSubDomains'
        null        | true              | null
    }
}