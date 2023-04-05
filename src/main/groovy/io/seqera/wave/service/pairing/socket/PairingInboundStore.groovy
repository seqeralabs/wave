package io.seqera.wave.service.pairing.socket

import java.time.Duration
import javax.annotation.PostConstruct

import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.data.future.AbstractFutureStore
import io.seqera.wave.service.data.future.FutureQueue
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton
/**
 * Model an distribute store for completable future that
 * used to collect inbound messages
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class PairingInboundStore extends AbstractFutureStore<PairingMessage> {

    @Value('${wave.pairing.channel.timeout:5s}')
    private Duration timeout

    @Value('${wave.pairing.channel.awaitTimeout:100ms}')
    private Duration poolInterval

    PairingInboundStore(FutureQueue<String> publisher) {
        super(publisher, new MoshiEncodeStrategy<PairingMessage>() {})
    }

    @PostConstruct
    private void init() {
        start()
    }

    @Override
    String topic() {
        return "pairing-inbound-queue/v1:"
    }

    String name() { "inbound-queue" }

    @Override
    Duration timeout() {
        return timeout
    }

    @Override
    Duration pollInterval() { poolInterval }
}
