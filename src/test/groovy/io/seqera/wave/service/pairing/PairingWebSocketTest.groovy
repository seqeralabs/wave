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

package io.seqera.wave.service.pairing

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.service.pairing.socket.PairingWebSocket

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class PairingWebSocketTest extends Specification {

    def 'should allow any host' () {
        given:
        def ctx = ApplicationContext.run()
        def pairing = ctx.getBean(PairingWebSocket)

        expect:
        !pairing.isDenyHost('foo')
        !pairing.isDenyHost('seqera.io')
        !pairing.isDenyHost('ngrok')

        cleanup:
        ctx.close()
    }

    def 'should disallowed deny hosts' () {
        given:
        def ctx = ApplicationContext.run(['wave.denyHosts': ['ngrok','hctal']])
        def pairing = ctx.getBean(PairingWebSocket)

        expect:
        pairing.isDenyHost('ngrok')
        pairing.isDenyHost('hctal')
        and:
        !pairing.isDenyHost('seqera.io')

        cleanup:
        ctx.close()
    }
}
