package io.seqera.wave.service.pairing.socket

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.scheduling.TaskScheduler
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import static io.seqera.wave.util.RegHelper.random256Hex
/**
 * Implements Wave pairing websocket server
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Prototype  // note use prototype to have an instance for each session
@ServerWebSocket("/pairing/{service}/token/{token}{?endpoint}")
class PairingWebSocket {
    private static final CloseReason INVALID_ENDPOINT_REGISTER = new CloseReason(4000, "Invalid endpoint register")

    private WebSocketSession session
    private String service
    private String endpoint
    private String token

    private PairingChannel channel
    private PairingService pairingService
    private TaskScheduler scheduler

    PairingWebSocket(PairingChannel channel, PairingService pairingService, TaskScheduler scheduler) {
        this.channel = channel
        this.pairingService = pairingService
        this.scheduler = scheduler
    }

    @OnOpen
    void onOpen(String service, String token, String endpoint, WebSocketSession session) {
        log.debug "New '$service' pairing session - endpoint: ${endpoint} [sessionId: $session.id]"
        this.service = service
        this.endpoint = endpoint
        this.session = session
        this.token = token

        // Register endpoint
        channel.registerWebsocketSession(session)

        // acquire a pairing
        final resp = this.pairingService.acquirePairingKey(service, endpoint)
        session.sendAsync(new PairingResponse(
                msgId: random256Hex(),
                pairingId: resp.pairingId,
                publicKey: resp.publicKey
        ))

    }

    @OnMessage
    void onMessage(PairingMessage message) {
        log.debug "Receiving '$service' message=$message [sessionId: $session.id]"
        if( message instanceof PairingHeartbeat ) {
            channel.registerWebsocketSession(session)
            return
        }

        channel.receiveResponse(service, endpoint, message)
    }

    @OnClose
    void onClose() {
        log.debug "Closing '$service' pairing session [sessionId: $session.id]"
        channel.deregisterWebsocketSession(session)
    }

}
