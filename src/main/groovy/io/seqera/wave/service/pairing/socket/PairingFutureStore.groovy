package io.seqera.wave.service.pairing.socket

import java.time.Duration

import io.micronaut.context.annotation.Value
import io.seqera.wave.service.data.future.AbstractFutureStore
import io.seqera.wave.service.data.future.FuturePublisher
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class PairingFutureStore extends AbstractFutureStore<PairingMessage> {

    PairingFutureStore(FuturePublisher<String> publisher, @Value('${wave.pairing.timeout:5s}') Duration timeout) {
        super(publisher, timeout)
    }

    @Override
    String group() {
        return "pairing-store"
    }
}
