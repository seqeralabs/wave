/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
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

package io.seqera.wave.service.security

import spock.lang.Specification
import spock.lang.Unroll

import io.seqera.wave.configuration.SsrfConfig
import io.seqera.wave.exception.BadRequestException
/**
 *
 * @author Oussama El Azizi <oussama.elazizi@seqera.io>
 */
class SsrfProtectionServiceTest extends Specification {

    private SsrfProtectionService service(boolean enabled, List<String> allowlist=null) {
        new SsrfProtectionService(config: new SsrfConfig(ssrfProtectionEnabled: enabled, registryAllowlist: allowlist))
    }

    @Unroll
    def 'should reject host #host when protection is enabled'() {
        when:
        service(true).checkHost(host)
        then:
        thrown(BadRequestException)

        where:
        host << [
                '127.0.0.1',
                '0.0.0.0',
                'localhost',
                '10.0.0.1',
                '172.16.0.1',
                '192.168.1.1',
                '169.254.169.254',
                '169.254.170.2' ]
    }

    @Unroll
    def 'should allow public host #host when protection is enabled'() {
        when:
        service(true).checkHost(host)
        then:
        noExceptionThrown()

        where:
        host << [ '8.8.8.8', '1.1.1.1' ]
    }

    def 'should be a no-op when protection is disabled'() {
        when:
        service(false).checkHost('127.0.0.1')
        and:
        service(false).checkHost('169.254.169.254')
        then:
        noExceptionThrown()
    }

    def 'should validate the endpoint host'() {
        when:
        service(true).checkEndpoint(new URI('https://10.0.0.1:8080/v2/'))
        then:
        thrown(BadRequestException)

        when:
        service(false).checkEndpoint(new URI('https://10.0.0.1:8080/v2/'))
        then:
        noExceptionThrown()
    }

    @Unroll
    def 'host #host blocked by range rules should be allowed when allow-listed by IP/CIDR #allowlist'() {
        when:
        service(true, allowlist).checkHost(host)
        then:
        noExceptionThrown()

        where:
        host                | allowlist
        '10.0.0.5'          | ['10.0.0.5']            // exact internal IP
        '10.0.0.5'          | ['10.0.0.0/24']         // internal CIDR
        '10.0.0.5'          | ['docker.io', '10.0.0.0/8']
        '169.254.169.254'   | ['169.254.169.254']     // operator explicitly permits metadata IP
        '127.0.0.1'         | ['127.0.0.0/8']
    }

    @Unroll
    def 'host #host blocked by range rules should stay blocked for allowlist #allowlist'() {
        when:
        service(true, allowlist).checkHost(host)
        then:
        thrown(BadRequestException)

        where:
        host           | allowlist
        '10.0.0.5'     | null                 // no allowlist
        '10.0.0.5'     | ['docker.io']        // only a hostname entry — no IP/CIDR exemption
        '10.0.0.5'     | ['*.example.com']    // wildcard does not exempt an IP
        '10.0.0.5'     | ['10.0.1.0/24']      // CIDR that does not cover the host
        '10.0.0.5'     | ['2001:db8::/32']    // IPv6 CIDR cannot exempt an IPv4 host
        'localhost'    | ['10.0.0.0/8']       // a non-IP host is never exempt by IP/CIDR
    }

    def 'the endpoint host should be exempt when allow-listed by CIDR'() {
        when:
        service(true, ['10.0.0.0/24']).checkEndpoint(new URI('https://10.0.0.5:5000/v2/'))
        then:
        noExceptionThrown()
    }

    @Unroll
    def 'access to #registry with allowlist #allowlist should be allowed'() {
        when:
        service(true, allowlist).checkRegistryAllowed(registry)
        then:
        noExceptionThrown()

        where:
        registry                                    | allowlist
        'docker.io'                                 | ['docker.io', 'quay.io']
        'quay.io'                                   | ['docker.io', 'quay.io']
        'quay.io:443'                               | ['docker.io', 'quay.io']
        'QUAY.IO'                                   | ['docker.io', 'quay.io']
        '12345.dkr.ecr.eu-west-1.amazonaws.com'     | ['*.amazonaws.com']
        'amazonaws.com'                             | ['*.amazonaws.com']
        // IP literal entries
        '203.0.113.5'                               | ['203.0.113.5']
        '203.0.113.5:5000'                          | ['203.0.113.5']
        '2001:db8::1'                               | ['2001:db8::1']
        // CIDR entries
        '203.0.113.5'                               | ['203.0.113.0/24']
        '203.0.113.255'                             | ['203.0.113.0/24']
        '10.1.2.3'                                  | ['10.0.0.0/8']
        '2001:db8::abcd'                            | ['2001:db8::/32']
        // mixed allowlist
        '203.0.113.5'                               | ['docker.io', '203.0.113.0/24']
    }

    @Unroll
    def 'access to #registry with allowlist #allowlist should be rejected'() {
        when:
        service(true, allowlist).checkRegistryAllowed(registry)
        then:
        def e = thrown(BadRequestException)
        e.message.contains('is not in the configured registry allowlist')

        where:
        registry                                    | allowlist
        'ghcr.io'                                   | ['docker.io', 'quay.io']
        'kubernetes.default.svc'                    | ['docker.io', 'quay.io']
        'evil-amazonaws.com'                        | ['*.amazonaws.com']
        'amazonaws.com.evil.io'                     | ['*.amazonaws.com']
        // IP just outside the CIDR
        '203.0.114.1'                               | ['203.0.113.0/24']
        '10.1.2.3'                                  | ['10.0.0.0/16']
        // different IP literal
        '203.0.113.6'                               | ['203.0.113.5']
        // a hostname is NOT matched by a CIDR (no DNS resolution)
        'registry.internal'                         | ['10.0.0.0/8']
        // IPv4 host must not match an IPv6 CIDR and vice-versa
        '203.0.113.5'                               | ['2001:db8::/32']
    }

    def 'allowlist applies to every request regardless of caller'() {
        when: 'an unlisted registry is referenced'
        service(true, ['docker.io']).checkRegistryAllowed('ghcr.io')
        then:
        thrown(BadRequestException)
    }

    def 'allowlist should not be applied when empty or protection is disabled'() {
        when: 'no allowlist is configured'
        service(true, null).checkRegistryAllowed('ghcr.io')
        then:
        noExceptionThrown()

        when: 'the allowlist is empty'
        service(true, []).checkRegistryAllowed('ghcr.io')
        then:
        noExceptionThrown()

        when: 'protection is disabled'
        service(false, ['docker.io']).checkRegistryAllowed('ghcr.io')
        then:
        noExceptionThrown()
    }

}
