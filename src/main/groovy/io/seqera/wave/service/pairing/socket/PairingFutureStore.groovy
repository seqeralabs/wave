package io.seqera.wave.service.pairing.socket

import java.time.Duration

import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.data.future.AbstractFutureStore
import io.seqera.wave.service.data.future.FutureQueue
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class PairingFutureStore extends AbstractFutureStore<PairingMessage> {

    @Value('${wave.pairing.channel.timeout:5s}')
    private Duration timeout

    PairingFutureStore(FutureQueue<String> publisher) {
        super(publisher, new MoshiEncodeStrategy<PairingMessage>() {})
    }

    @Override
    String topic() {
        return "pairing-future-channel/v1:"
    }

    @Override
    Duration timeout() {
        return timeout
    }
}
