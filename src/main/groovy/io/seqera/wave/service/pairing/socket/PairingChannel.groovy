package io.seqera.wave.service.pairing.socket

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketSession
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Handle sending and replies for pairing messages
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingChannel {

    @Inject
    private PairingFutureStore futuresStore

    @Inject
    private PairingEndpointsStore endpointsStore

    @Inject
    private WebSocketBroadcaster broadcaster

    @Value('${wave.pairing.channel.timeout:5s}')
    private Duration timeout

    void registerWebsocketSession(WebSocketSession session) {
        endpointsStore.addWebsocketSession(session)
    }

    void deregisterWebsocketSession(WebSocketSession session) {
        endpointsStore.removeWebsocketSession(session)
    }

    boolean hasWebsocketSession(String service, String endpoint) {
        return endpointsStore.hasWebsocketSession(service, endpoint)
    }

    public <M extends PairingMessage, R extends PairingMessage> CompletableFuture<R> sendRequest(String service, String endpoint, M message) {
        log.debug "Request message=${message.class.simpleName} to endpoint='$endpoint'"

        // create a unique Id to identify this command
        final result = futuresStore
                .create(message.msgId)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)

        // publish
        final sessionId = endpointsStore.getWebsocketSessionId(service,endpoint)
        log.debug "Broadcasting message to $service endpoint: '$endpoint' [sessionId: $sessionId]"
        broadcaster.broadcastAsync(message, (sess) -> sess.id==sessionId)

        // return the future to the caller
        return (CompletableFuture<R>) result
    }

    void receiveResponse(String service, String endpoint, PairingMessage message) {
        futuresStore.complete(message.msgId, message)
    }

}
