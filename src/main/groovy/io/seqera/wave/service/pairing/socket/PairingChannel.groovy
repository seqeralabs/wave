package io.seqera.wave.service.pairing.socket

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketSession
import io.seqera.wave.service.pairing.socket.msg.PairingPayload
import io.seqera.wave.service.pairing.socket.msg.PairingReply
import io.seqera.wave.service.pairing.socket.msg.PairingSend
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Handle sending and replies for pairing messages
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingChannel implements PairingListener {

    @Inject
    private WebSocketBroadcaster broadcaster

    @Inject
    private PairingStore futuresStore

    void onReply(String service, String pairingId, PairingReply reply)  {
        futuresStore.complete(reply.msgId, reply.payload)
    }

    <M extends PairingPayload, R extends PairingPayload> CompletableFuture<R> send(String endpoint, M message) {
        log.debug "Brodcast message=$message to endpoint='$endpoint'"
        // create a unique Id to identify this command
        final msgId = UUID.randomUUID().toString()
        final request = new PairingSend(msgId, message)
        // create a future to hold the response when it will to be sent
        final result = futuresStore.create(msgId)
        // broadcast the message to the target pairing id
        broadcaster.broadcastAsync(request, (WebSocketSession sess) -> {
            log.debug "Checking session=$sess; matches=${endpoint==sess.requestParameters.get('endpoint',String,null)}"
            endpoint==sess.requestParameters.get('endpoint',String,null) } )
        // return the future to the caller
        return (CompletableFuture<R>)result
    }

    void close(String service, String pairingId) {
        futuresStore.close()
    }
}
