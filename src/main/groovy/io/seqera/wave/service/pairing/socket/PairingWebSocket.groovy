package io.seqera.wave.service.pairing.socket

import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
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
class PairingWebSocket implements Consumer<PairingMessage> {

    private WebSocketSession session
    private String service
    private String endpoint
    private String token

    private PairingChannel channel
    private PairingService pairingService

    PairingWebSocket(PairingChannel channel, PairingService pairingService) {
        this.channel = channel
        this.pairingService = pairingService
    }

    @OnOpen
    void onOpen(String service, String token, String endpoint, WebSocketSession session) {
        log.debug "Opening pairing session - endpoint: ${endpoint} [sessionId: $session.id]"
        this.service = service
        this.endpoint = endpoint
        this.session = session
        this.token = token

        // Register endpoint
        channel.registerConsumer(service, endpoint, this)

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
        if( message instanceof PairingHeartbeat ) {
            log.debug "Receiving heartbeat - endpoint: ${endpoint} [sessionId: $session.id]"
            // send pong message
            final pong = new PairingHeartbeat(msgId:random256Hex())
            session.sendAsync(pong)
            return
        }
        else {
            log.trace "Receiving message=$message - endpoint: ${endpoint} [sessionId: $session.id]"
        }

        channel.receiveResponse(message)
    }

    @OnClose
    void onClose() {
        log.debug "Closing pairing session - endpoint: ${endpoint} [sessionId: $session.id]"
        channel.deregisterConsumer(service, endpoint)
    }

    @Override
    void accept(PairingMessage pairingMessage) {
        log.trace "Sending message=$pairingMessage"
        session.sendSync(pairingMessage)
    }
}
