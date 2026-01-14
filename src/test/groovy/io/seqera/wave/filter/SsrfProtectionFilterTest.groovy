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

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Tests for SsrfProtectionFilter
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class SsrfProtectionFilterTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Unroll
    def 'should block private IP addresses: #host'() {
        given:
        def filter = new SsrfProtectionFilter()

        when:
        filter.validateHost(host)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('private') || e.message.contains('loopback') || e.message.contains('link-local') || e.message.contains('metadata') || e.message.contains('localhost')

        where:
        host << [
            '10.0.0.1',
            '10.255.255.255',
            '172.16.0.1',
            '172.31.255.255',
            '192.168.1.1',
            '192.168.255.255',
            '127.0.0.1',
            '127.0.0.2',
            '169.254.169.254',  // AWS metadata service
            '0.0.0.0'
        ]
    }

    @Unroll
    def 'should block localhost variations: #host'() {
        given:
        def filter = new SsrfProtectionFilter()

        when:
        filter.validateHost(host)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('localhost') || e.message.contains('loopback')

        where:
        host << [
            'localhost',
            'LOCALHOST',
            'localhost.localdomain'
        ]
    }

    @Unroll
    def 'should allow public hostnames: #host'() {
        given:
        def filter = new SsrfProtectionFilter()

        when:
        filter.validateHost(host)

        then:
        noExceptionThrown()

        where:
        host << [
            'docker.io',
            'registry-1.docker.io',
            'quay.io',
            'ghcr.io',
            'gcr.io',
            'public.ecr.aws',
            'example.com',
            'github.com'
        ]
    }

    @Unroll
    def 'should validate URIs with public hosts: #url'() {
        given:
        def filter = new SsrfProtectionFilter()
        def uri = new URI(url)

        when:
        filter.validateUri(uri)

        then:
        noExceptionThrown()

        where:
        url << [
            'https://docker.io',
            'https://registry-1.docker.io',
            'https://quay.io/v2/',
            'http://example.com',
            'https://github.com/path/to/resource'
        ]
    }

    @Unroll
    def 'should block URIs with private IPs: #url'() {
        given:
        def filter = new SsrfProtectionFilter()
        def uri = new URI(url)

        when:
        filter.validateUri(uri)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('private') || e.message.contains('loopback') || e.message.contains('localhost') || e.message.contains('metadata') || e.message.contains('link-local')

        where:
        url << [
            'http://127.0.0.1',
            'https://localhost',
            'http://10.0.0.1',
            'http://192.168.1.1',
            'http://172.16.0.1',
            'http://169.254.169.254'  // AWS metadata service
        ]
    }

    @Unroll
    def 'should reject invalid URL schemes: #url'() {
        given:
        def filter = new SsrfProtectionFilter()
        def uri = new URI(url)

        when:
        filter.validateUri(uri)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('scheme')

        where:
        url << [
            'file:///etc/passwd',
            'ftp://example.com',
            'gopher://example.com',
            'dict://example.com'
        ]
    }

    def 'should handle null inputs'() {
        given:
        def filter = new SsrfProtectionFilter()

        when:
        filter.validateUri(null)

        then:
        thrown(IllegalArgumentException)

        when:
        filter.validateHost(null)

        then:
        thrown(IllegalArgumentException)

        when:
        filter.validateHost('')

        then:
        thrown(IllegalArgumentException)
    }

    def 'should block cloud metadata service IPs'() {
        given:
        def filter = new SsrfProtectionFilter()

        when:
        filter.validateHost('169.254.169.254')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('metadata')
    }

    def 'should validate IP addresses using InetAddress'() {
        given:
        def filter = new SsrfProtectionFilter()

        when:
        filter.validateIpAddress(InetAddress.getByName('8.8.8.8'))

        then:
        noExceptionThrown()

        when:
        filter.validateIpAddress(InetAddress.getByName('127.0.0.1'))

        then:
        thrown(IllegalArgumentException)
    }

    def 'filter should throw HttpStatusException for blocked requests'() {
        given:
        def filter = new SsrfProtectionFilter()
        def request = HttpRequest.GET(new URI('http://127.0.0.1'))

        when:
        filter.requestFilter(request)

        then:
        thrown(HttpStatusException)
    }

    def 'filter should allow valid requests'() {
        given:
        def filter = new SsrfProtectionFilter()
        def request = HttpRequest.GET(new URI('https://docker.io'))

        when:
        filter.requestFilter(request)

        then:
        noExceptionThrown()
    }
}