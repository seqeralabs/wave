package io.seqera.wave.service.pairing.socket


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import jakarta.inject.Singleton
import static io.seqera.wave.util.RegHelper.random256Hex
/**
 * Implements Wave pairing websocket server
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
@ServerWebSocket("/pairing/{service}/token/{token}{?endpoint}")
class PairingWebSocket {

    private PairingChannel channel
    private PairingService pairingService

    PairingWebSocket(PairingChannel channel, PairingService pairingService) {
        this.channel = channel
        this.pairingService = pairingService
    }

    @OnOpen
    void onOpen(String service, String token, String endpoint, WebSocketSession session) {
        log.debug "Opening pairing session - endpoint: ${endpoint} [sessionId: $session.id]"

        // Register the client and the sender callback that it's needed to deliver
        // the message to the remote client
        channel.registerClient(service, endpoint, session.id,(pairingMessage) -> {
            log.trace "Websocket send message=$pairingMessage"
            session
                    .sendSync(pairingMessage)
        })

        // acquire a pairing key and send it to the remote client
        final resp = this.pairingService.acquirePairingKey(service, endpoint)
        session.sendAsync(new PairingResponse(
                msgId: random256Hex(),
                pairingId: resp.pairingId,
                publicKey: resp.publicKey
        ))
    }

    @OnMessage
    void onMessage(String service, String token, String endpoint, PairingMessage message, WebSocketSession session) {
        if( message instanceof PairingHeartbeat ) {
            log.trace "Receiving heartbeat - endpoint: ${endpoint} [sessionId: $session.id]"
            // send pong message
            final pong = new PairingHeartbeat(msgId:random256Hex())
            session.sendAsync(pong)
        }
        else {
            // Collect a reply from the client and takes care to dispatch it
            // to the source instance.
            log.trace "Receiving message=$message - endpoint: ${endpoint} [sessionId: $session.id]"
            channel.receiveResponse(message)
        }
    }

    @OnClose
    void onClose(String service, String token, String endpoint, WebSocketSession session) {
        log.debug "Closing pairing session - endpoint: ${endpoint} [sessionId: $session.id]"
        channel.unregisterClient(service, endpoint, session.id)
    }

}
