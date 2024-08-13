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

package io.seqera.wave.service.pairing.socket

import java.time.Duration
import javax.annotation.PreDestroy

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.data.queue.AbstractMessageQueue
import io.seqera.wave.service.data.queue.MessageQueue
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton
/**
 * Implement a distributed queue for Wave pairing messages
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Singleton
@CompileStatic
class PairingOutboundQueue extends AbstractMessageQueue<PairingMessage> {

    final private Duration pollInterval

    PairingOutboundQueue(
            MessageQueue<String> broker,
            @Value('${wave.pairing.channel.awaitTimeout:100ms}') Duration pollInterval
    ) {
        super(broker)
        this.pollInterval = pollInterval
    }

    @Override
    protected String prefix() {
        return 'pairing-outbound-queue/v1:'
    }

    @Override
    protected String name() { "outbound-queue" }

    @Override
    protected Duration pollInterval() { return pollInterval }

    @PreDestroy
    void close() {
        super.close()
    }
}
