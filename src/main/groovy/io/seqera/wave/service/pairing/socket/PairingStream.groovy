package io.seqera.wave.service.pairing.socket

import groovy.transform.CompileStatic
import io.seqera.wave.service.data.stream.AbstractMessageStream
import io.seqera.wave.service.data.stream.MessageBroker
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton


/**
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Singleton
@CompileStatic
class PairingStream extends AbstractMessageStream<PairingMessage> {

    PairingStream(MessageBroker<String> broker) {
        super(broker)
    }

    @Override
    protected String getPrefix() {
        return 'wave-pairing-stream/v1:'
    }
}
