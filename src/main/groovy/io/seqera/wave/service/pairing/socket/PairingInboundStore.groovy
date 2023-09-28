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

package io.seqera.wave.service.pairing.socket

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.data.future.AbstractFutureStore
import io.seqera.wave.service.data.future.FutureHash
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton

/**
 * Model an distribute store for completable future that
 * used to collect inbound messages
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class PairingInboundStore extends AbstractFutureStore<PairingMessage> {

    @Value('${wave.pairing.channel.timeout:5s}')
    Duration timeout

    PairingInboundStore(FutureHash<String> publisher) {
        super(publisher, new MoshiEncodeStrategy<PairingMessage>() {})
    }

    @Override
    String prefix() {
        return "pairing-inbound-queue/v1:"
    }

    String name() { "inbound-queue" }

}
