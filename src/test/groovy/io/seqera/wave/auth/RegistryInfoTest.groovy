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

package io.seqera.wave.auth

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegistryInfoTest extends Specification {

    def 'should populate registry info' () {
        when:
        def uri1 = new URI('http://my.docker.io/v2')
        def reg1 = new RegistryInfo('docker.io', uri1, new RegistryAuth(uri1, 'foo', RegistryAuth.Type.Basic))
        then:
        reg1.name == 'docker.io'
        reg1.host == new URI('http://my.docker.io')
        reg1.auth.realm == uri1
        reg1.auth.service == 'foo'
        reg1.auth.type == RegistryAuth.Type.Basic
        reg1.auth.endpoint == new URI('http://my.docker.io/v2?service=foo')
    }

    def 'should implement equals and hashcode' () {
        given:
        def uri1 = new URI('http://my.docker.io/v2')
        def reg1 = new RegistryInfo('docker.io', uri1, new RegistryAuth(uri1, 'foo', RegistryAuth.Type.Basic))
        and:
        def uri2 = new URI('http://my.docker.io/v2')
        def reg2 = new RegistryInfo('docker.io', uri2, new RegistryAuth(uri2, 'foo', RegistryAuth.Type.Basic))
        and:
        def uri3 = new URI('http://my.quay.io/v2')
        def reg3 = new RegistryInfo('quay.io', uri2, new RegistryAuth(uri3, 'foo', RegistryAuth.Type.Basic))

        expect:
        reg1 == reg2
        reg1 != reg3
        and:
        reg1.hashCode() == reg2.hashCode()
        reg1.hashCode() != reg3.hashCode()
    }
}
