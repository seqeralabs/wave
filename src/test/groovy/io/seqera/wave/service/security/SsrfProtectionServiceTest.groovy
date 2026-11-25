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
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
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
    def 'anonymous access to #registry with allowlist #allowlist should be allowed'() {
        when:
        service(true, allowlist).checkAnonymousRegistryAccess(registry, PlatformId.NULL)
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
    }

    @Unroll
    def 'anonymous access to #registry with allowlist #allowlist should be rejected'() {
        when:
        service(true, allowlist).checkAnonymousRegistryAccess(registry, PlatformId.NULL)
        then:
        def e = thrown(BadRequestException)
        e.message.contains('is not allowed for anonymous requests')

        where:
        registry                                    | allowlist
        'ghcr.io'                                   | ['docker.io', 'quay.io']
        'kubernetes.default.svc'                    | ['docker.io', 'quay.io']
        'evil-amazonaws.com'                        | ['*.amazonaws.com']
        'amazonaws.com.evil.io'                     | ['*.amazonaws.com']
    }

    def 'authenticated identity should not be restricted by the allowlist'() {
        given:
        def srv = service(true, ['docker.io'])
        def identity = new PlatformId(new User(id:1), 100, 'token')

        when:
        srv.checkAnonymousRegistryAccess('ghcr.io', identity)
        then:
        noExceptionThrown()
    }

    def 'null identity should be treated as anonymous'() {
        when:
        service(true, ['docker.io']).checkAnonymousRegistryAccess('ghcr.io', null)
        then:
        thrown(BadRequestException)
    }

    def 'allowlist should not be applied when empty or protection is disabled'() {
        when: 'no allowlist is configured'
        service(true, null).checkAnonymousRegistryAccess('ghcr.io', PlatformId.NULL)
        then:
        noExceptionThrown()

        when: 'the allowlist is empty'
        service(true, []).checkAnonymousRegistryAccess('ghcr.io', PlatformId.NULL)
        then:
        noExceptionThrown()

        when: 'protection is disabled'
        service(false, ['docker.io']).checkAnonymousRegistryAccess('ghcr.io', PlatformId.NULL)
        then:
        noExceptionThrown()
    }

}
