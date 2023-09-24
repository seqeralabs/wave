/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.auth

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegistryAuthTest extends Specification {

    def 'should populate object' () {
        when:
        def auth1 = new RegistryAuth(new URI('http://foo.com'), 'bar', RegistryAuth.Type.Bearer)
        then:
        auth1.realm == new URI('http://foo.com')
        auth1.service == 'bar'
        auth1.type == RegistryAuth.Type.Bearer
        auth1.isRefreshable()
        auth1.endpoint == new URI('http://foo.com?service=bar')

        when:
        def auth2 = new RegistryAuth(new URI('http://this.com'), 'that', RegistryAuth.Type.Basic)
        then:
        auth2.realm == new URI('http://this.com')
        auth2.service == 'that'
        auth2.type == RegistryAuth.Type.Basic
        !auth2.isRefreshable()
        auth2.endpoint == new URI('http://this.com?service=that')
    }

    def 'should implement equals and hashcode' () {
       given:
       def auth1 = new RegistryAuth(new URI('http://foo.com'), 'bar', RegistryAuth.Type.Bearer)
       def auth2 = new RegistryAuth(new URI('http://foo.com'), 'bar', RegistryAuth.Type.Bearer)
       def auth3 = new RegistryAuth(new URI('http://this.com'), 'that', RegistryAuth.Type.Basic)

        expect:
        auth1 == auth2
        auth1 != auth3
        and:
        auth1.hashCode() == auth2.hashCode()
        auth1.hashCode() != auth3.hashCode()
    }

    def 'should parse registry info'() {
        expect:
        RegistryAuth.parse('Bearer realm="https://quay.io/v2/auth",service="quay.io"')
                == new RegistryAuth(new URI('https://quay.io/v2/auth'), 'quay.io', RegistryAuth.Type.Bearer)

        and:
        RegistryAuth.parse('Bearer realm="https://auth.docker.io/token",service="registry.docker.io"')
                == new RegistryAuth(new URI('https://auth.docker.io/token'), 'registry.docker.io', RegistryAuth.Type.Bearer)

        and:
        RegistryAuth.parse('Basic realm="http://foo",service="bar"')
                == new RegistryAuth(new URI('http://foo'), 'bar', RegistryAuth.Type.Basic)

        and:
        RegistryAuth.parse('Basic realm="http://foo"')
                == new RegistryAuth(new URI('http://foo'), null, RegistryAuth.Type.Basic)

        and:
        RegistryAuth.parse('Bearer realm="https://gitea.dev-tower.net/v2/token",service="container_registry",scope="*"')
            == new RegistryAuth(new URI('https://gitea.dev-tower.net/v2/token'), 'container_registry', RegistryAuth.Type.Bearer)

        and:
        RegistryAuth.parse('foo') == null
        RegistryAuth.parse(null) == null
    }
}
