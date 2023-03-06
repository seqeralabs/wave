package io.seqera.wave.service.pairing.socket

import io.micronaut.context.annotation.Value
import io.seqera.wave.service.data.AbstractFutureStore
import io.seqera.wave.service.data.FuturePublisher
import io.seqera.wave.service.pairing.socket.msg.PairingPayload
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class PairingStore extends AbstractFutureStore<PairingPayload> {

    @Value('${wave.pairing.store-group:`pairing-store`}')
    private String groupName

    PairingStore(FuturePublisher<String> publisher) {
        super(publisher)
    }

    @Override
    String group() {
        return groupName
    }
}
