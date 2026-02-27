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
 * @author Munish Chouhan <munish.chouhan@seqera.io>
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

    def 'should reject null or empty inputs'() {
        when:
        SsrfValidator.validateHost(null)

        then:
        thrown(IllegalArgumentException)

        when:
        SsrfValidator.validateHost('')

        then:
        thrown(IllegalArgumentException)
    }

    def 'should reject cloud metadata service IPs'() {
        when:
        SsrfValidator.validateHost('169.254.169.254')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('metadata')
    }
}