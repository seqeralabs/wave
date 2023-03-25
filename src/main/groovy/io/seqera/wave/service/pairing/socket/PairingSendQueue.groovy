package io.seqera.wave.service.pairing.socket

import io.seqera.wave.service.data.queue.AbstractConsumerQueue
import io.seqera.wave.service.data.queue.QueueBroker
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton

/**
 * FIFO queue to handle all pairing messages send to tower.
 * It can has multiple consumer on any Wave instance but
 * only one will consume the message.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Singleton
class PairingSendQueue extends AbstractConsumerQueue<PairingMessage> {

    PairingSendQueue(QueueBroker<String> broker) {
        super(broker)
    }

    @Override
    String topic() {
        return "pairing-send-channel"
    }
}
