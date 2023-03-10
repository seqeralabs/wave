package io.seqera.wave.service.pairing.socket

import io.micronaut.context.annotation.Value
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

    @Value('${wave.pairing.queue-group:`pairing-queue`}')
    private String groupName

    PairingSendQueue(QueueBroker<String> broker) {
        super(broker)
    }

    @Override
    String group() {
        return groupName
    }
}
