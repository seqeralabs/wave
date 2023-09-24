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

package io.seqera.wave.core.spec

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ConfigSpecTest extends Specification {

    def 'should create a config spec' () {
        when:
        def config1 = new ConfigSpec()
        then:
        config1.hostName == null
        config1.domainName == null
        config1.workingDir == null
        config1.user == null
        and:
        !config1.attachStdin
        !config1.attachStdout
        !config1.attachStderr
        and:
        config1.env == []
        config1.cmd == []
        config1.entrypoint == []

        when:
        def config2 = new ConfigSpec(
                Hostname: 'foo',
                Domainname: 'bar',
                User: 'Me',
                AttachStdin: true,
                AttachStdout: true,
                AttachStderr: true,
                Cmd: ['this','that'],
                Env: ['Foo=1', 'Bar=2'],
                Entrypoint: ['some','entry']
        )
        then:
        config2.hostName == 'foo'
        config2.domainName == 'bar'
        config2.user == 'Me'
        and:
        config2.attachStdin
        config2.attachStdout
        config2.attachStderr
        and:
        config2.env == ['Foo=1', 'Bar=2']
        config2.cmd == ['this','that']
        config2.entrypoint == ['some','entry']
    }
}
