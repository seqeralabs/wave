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

import java.time.Instant
import java.util.concurrent.CompletableFuture

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketSession
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.pairing.socket.PairingWebSocket
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat

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

    def 'should close session on heartbeat when pairing record is missing from store'() {
        given:
        def pairingService = Mock(PairingService)
        def channel = Mock(PairingChannel)
        def session = Mock(WebSocketSession)
        and:
        def ws = new PairingWebSocket()
        ws.@pairingService = pairingService
        ws.@channel = channel

        when: 'heartbeat received and pairing record is missing'
        ws.onMessage('tower', 'token', 'http://tower.io', new PairingHeartbeat(msgId: 'test'), session)

        then: 'the session is closed to force a reconnect'
        1 * pairingService.getPairingRecord('tower', 'http://tower.io') >> null
        1 * session.close(CloseReason.GOING_AWAY)
        0 * session.sendAsync(_)
    }

    def 'should not re-acquire pairing record on heartbeat when present in store'() {
        given:
        def pairingService = Mock(PairingService)
        def channel = Mock(PairingChannel)
        def session = Mock(WebSocketSession)
        and:
        def existingRecord = new PairingRecord(
                service: 'tower',
                endpoint: 'http://tower.io',
                pairingId: 'abc',
                expiration: Instant.now().plusSeconds(3600))
        and:
        def ws = new PairingWebSocket()
        ws.@pairingService = pairingService
        ws.@channel = channel

        when: 'heartbeat received and pairing record exists'
        ws.onMessage('tower', 'token', 'http://tower.io', new PairingHeartbeat(msgId: 'test'), session)

        then: 'the pairing record is checked but not re-acquired'
        1 * pairingService.getPairingRecord('tower', 'http://tower.io') >> existingRecord
        0 * pairingService.acquirePairingKey(_, _)
        1 * session.sendAsync(_) >> CompletableFuture.completedFuture(null)
    }
}
