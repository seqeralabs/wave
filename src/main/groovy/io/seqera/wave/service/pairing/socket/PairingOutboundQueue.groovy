package io.seqera.wave.service.pairing.socket

import java.time.Duration
import javax.annotation.PreDestroy

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
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

    private Duration pollInterval

    PairingOutboundQueue(
            MessageBroker<String> broker,
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
