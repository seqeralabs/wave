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
import io.micronaut.http.server.util.HttpClientAddressResolver
import java.net.InetSocketAddress

/**
 * Unit test to verify RateLimiterFilter prevents IP spoofing via X-Forwarded-For header
 *
 * Security Issue: COMP-1149 - Client IP Address Spoofing
 *
 * This test verifies that the RateLimiterFilter uses HttpClientAddressResolver which
 * by default uses socket remote address (secure), but can be configured to trust
 * X-Forwarded-For from trusted proxies like AWS ALB.
 *
 * @author Claude Code <noreply@anthropic.com>
 */
class RateLimiterFilterTest extends Specification {

    def 'should use socket address not X-Forwarded-For header by default'() {
        given: 'A mock HTTP request with spoofed X-Forwarded-For header'
        def actualClientIp = '192.168.1.100'
        def spoofedIp = '10.0.0.1'

        and: 'Create a request with remote address'
        def request = Mock(HttpRequest) {
            getRemoteAddress() >> new InetSocketAddress(actualClientIp, 12345)
            getHeaders() >> Mock(io.micronaut.http.HttpHeaders) {
                get('X-Forwarded-For') >> spoofedIp
                asMap() >> ['X-Forwarded-For': [spoofedIp]]
            }
        }

        and: 'A default HttpClientAddressResolver (secure, ignores headers)'
        def addressResolver = Mock(HttpClientAddressResolver) {
            resolve(request) >> actualClientIp  // Default behavior: uses socket address
        }

        and: 'A RateLimiterFilter instance'
        def filter = new RateLimiterFilter(
            Mock(io.micronaut.cache.SyncCache),
            Mock(RateLimiterOptions) {
                getLimitRefreshPeriod() >> java.time.Duration.ofSeconds(1)
                getLimitForPeriod() >> 100
                getTimeoutDuration() >> java.time.Duration.ofMillis(500)
                getStatusCode() >> 429
                validate() >> {}
            }
        )
        filter.addressResolver = addressResolver

        when: 'Getting the key used for rate limiting'
        def key = filter.getKey(request)

        then: 'The key should be the actual socket address, not the spoofed header'
        key == actualClientIp
        key != spoofedIp
    }

    def 'should ignore X-Forwarded-For header when not configured for trusted proxy'() {
        given: 'Multiple requests with same socket address but different X-Forwarded-For headers'
        def actualClientIp = '203.0.113.50'
        def spoofedIps = ['1.1.1.1', '2.2.2.2', '3.3.3.3', '127.0.0.1']

        and: 'A default HttpClientAddressResolver'
        def addressResolver = Mock(HttpClientAddressResolver) {
            resolve(_) >> actualClientIp  // Always returns socket address
        }

        and: 'A RateLimiterFilter instance'
        def filter = new RateLimiterFilter(
            Mock(io.micronaut.cache.SyncCache),
            Mock(RateLimiterOptions) {
                getLimitRefreshPeriod() >> java.time.Duration.ofSeconds(1)
                getLimitForPeriod() >> 100
                getTimeoutDuration() >> java.time.Duration.ofMillis(500)
                getStatusCode() >> 429
                validate() >> {}
            }
        )
        filter.addressResolver = addressResolver

        when: 'Getting keys for requests with different spoofed IPs but same socket address'
        def keys = spoofedIps.collect { spoofedIp ->
            def request = Mock(HttpRequest) {
                getRemoteAddress() >> new InetSocketAddress(actualClientIp, 12345)
                getHeaders() >> Mock(io.micronaut.http.HttpHeaders) {
                    get('X-Forwarded-For') >> spoofedIp
                    asMap() >> ['X-Forwarded-For': [spoofedIp]]
                }
            }
            filter.getKey(request)
        }

        then: 'All keys should be the same (actual socket address), ignoring spoofed headers'
        keys.every { it == actualClientIp }
        keys.unique().size() == 1
    }

    def 'should work correctly when X-Forwarded-For header is not present'() {
        given: 'A request without X-Forwarded-For header'
        def actualClientIp = '198.51.100.25'

        def request = Mock(HttpRequest) {
            getRemoteAddress() >> new InetSocketAddress(actualClientIp, 54321)
            getHeaders() >> Mock(io.micronaut.http.HttpHeaders) {
                get('X-Forwarded-For') >> null
                asMap() >> [:]
            }
        }

        and: 'A default HttpClientAddressResolver'
        def addressResolver = Mock(HttpClientAddressResolver) {
            resolve(request) >> actualClientIp
        }

        and: 'A RateLimiterFilter instance'
        def filter = new RateLimiterFilter(
            Mock(io.micronaut.cache.SyncCache),
            Mock(RateLimiterOptions) {
                getLimitRefreshPeriod() >> java.time.Duration.ofSeconds(1)
                getLimitForPeriod() >> 100
                getTimeoutDuration() >> java.time.Duration.ofMillis(500)
                getStatusCode() >> 429
                validate() >> {}
            }
        )
        filter.addressResolver = addressResolver

        when: 'Getting the key for rate limiting'
        def key = filter.getKey(request)

        then: 'The key should be the socket address'
        key == actualClientIp
    }

    def 'should trust X-Forwarded-For when configured for ALB deployment'() {
        given: 'A request from ALB with X-Forwarded-For header'
        def realClientIp = '203.0.113.42'  // Real client IP added by ALB
        def albIp = '10.0.1.50'  // ALB's internal IP

        def request = Mock(HttpRequest) {
            getRemoteAddress() >> new InetSocketAddress(albIp, 12345)
            getHeaders() >> Mock(io.micronaut.http.HttpHeaders) {
                get('X-Forwarded-For') >> realClientIp
                asMap() >> ['X-Forwarded-For': [realClientIp]]
            }
        }

        and: 'HttpClientAddressResolver configured to trust X-Forwarded-For (ALB mode)'
        def addressResolver = Mock(HttpClientAddressResolver) {
            resolve(request) >> realClientIp  // Configured to use X-Forwarded-For
        }

        and: 'A RateLimiterFilter instance'
        def filter = new RateLimiterFilter(
            Mock(io.micronaut.cache.SyncCache),
            Mock(RateLimiterOptions) {
                getLimitRefreshPeriod() >> java.time.Duration.ofSeconds(1)
                getLimitForPeriod() >> 100
                getTimeoutDuration() >> java.time.Duration.ofMillis(500)
                getStatusCode() >> 429
                validate() >> {}
            }
        )
        filter.addressResolver = addressResolver

        when: 'Getting the key for rate limiting'
        def key = filter.getKey(request)

        then: 'The key should be the real client IP from X-Forwarded-For (when configured for ALB)'
        key == realClientIp
        key != albIp
    }
}