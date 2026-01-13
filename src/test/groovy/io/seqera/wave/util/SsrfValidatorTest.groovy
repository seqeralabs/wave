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
import spock.lang.Unroll

/**
 * Tests for SsrfValidator utility class
 *
 * @author Munish Chouhan
 */
class SsrfValidatorTest extends Specification {

    @Unroll
    def 'should reject private IP addresses: #ip'() {
        when:
        SsrfValidator.validateHost(ip)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('private') || e.message.contains('loopback') || e.message.contains('link-local') || e.message.contains('metadata') || e.message.contains('localhost')

        where:
        ip << [
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
    def 'should reject localhost variations: #host'() {
        when:
        SsrfValidator.validateHost(host)

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
    def 'should accept public hostnames: #host'() {
        when:
        SsrfValidator.validateHost(host)

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
    def 'should validate URLs with scheme: #url'() {
        when:
        SsrfValidator.validateUrl(url)

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
    def 'should reject URLs with private IPs: #url'() {
        when:
        SsrfValidator.validateUrl(url)

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
        when:
        SsrfValidator.validateUrl(url)

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

    def 'should reject null or empty inputs'() {
        when:
        SsrfValidator.validateUrl(null)

        then:
        thrown(IllegalArgumentException)

        when:
        SsrfValidator.validateHost(null)

        then:
        thrown(IllegalArgumentException)

        when:
        SsrfValidator.validateHost('')

        then:
        thrown(IllegalArgumentException)
    }

    def 'should handle isSafeUrl helper method'() {
        expect:
        SsrfValidator.isSafeUrl('https://docker.io') == true
        SsrfValidator.isSafeUrl('http://127.0.0.1') == false
        SsrfValidator.isSafeUrl('http://10.0.0.1') == false
        SsrfValidator.isSafeUrl('file:///etc/passwd') == false
    }

    def 'should handle isSafeHost helper method'() {
        expect:
        SsrfValidator.isSafeHost('docker.io') == true
        SsrfValidator.isSafeHost('quay.io') == true
        SsrfValidator.isSafeHost('localhost') == false
        SsrfValidator.isSafeHost('127.0.0.1') == false
        SsrfValidator.isSafeHost('10.0.0.1') == false
    }

    def 'should reject cloud metadata service IPs'() {
        when:
        SsrfValidator.validateHost('169.254.169.254')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('metadata')
    }

    def 'should handle URI validation'() {
        when:
        SsrfValidator.validateUri(new URI('https://docker.io'))

        then:
        noExceptionThrown()

        when:
        SsrfValidator.validateUri(new URI('http://127.0.0.1'))

        then:
        thrown(IllegalArgumentException)
    }
}