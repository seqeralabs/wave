/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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

package io.seqera.wave.service.pairing.socket

import spock.lang.Specification

import io.seqera.service.pairing.socket.PairingMessageEncodeStrategy
import io.seqera.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.service.pairing.socket.msg.PairingResponse
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PairingMessageEncodeStrategyTest extends Specification {

    def 'should encode and decode pairing heartbeat message' () {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        and:
        def data = new PairingHeartbeat(msgId: 'foo')

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.msgId == data.msgId
    }

    def 'should encode and decode pairing response message' () {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        and:
        def data = new PairingResponse(msgId: 'foo', publicKey: 'key', pairingId: 'id')

        when:
        def json = encoder.encode(data)
        println json

        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy.msgId == data.msgId
        copy.publicKey == data.publicKey
        copy.pairingId == data.pairingId
    }

}
