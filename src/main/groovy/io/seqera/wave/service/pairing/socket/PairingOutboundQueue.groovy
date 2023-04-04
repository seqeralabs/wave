package io.seqera.wave.service.pairing.socket

import groovy.transform.CompileStatic
import io.seqera.wave.service.data.queue.AbstractMessageQueue
import io.seqera.wave.service.data.queue.MessageBroker
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

    PairingOutboundQueue(MessageBroker<String> broker) {
        super(broker)
    }

    @Override
    protected String getPrefix() {
        return 'pairing-outbound-queue/v1:'
    }
}
