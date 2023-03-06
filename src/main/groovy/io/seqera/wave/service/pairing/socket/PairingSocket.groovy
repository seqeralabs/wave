package io.seqera.wave.service.pairing.socket

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingReply
import io.seqera.wave.service.pairing.socket.msg.PairingSend

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Prototype  // note use prototype to have an instance for each session
@ServerWebSocket("/pairing/{service}/{pairingId}{?endpoint}")
class PairingSocket {

    private final PairingListener listener
    private WebSocketSession session
    private String service
    private String pairingId
    private String endpoint

    private PairingService pairingService

    PairingSocket(PairingListener broadcaster, PairingService pairingService) {
        this.listener = broadcaster
        this.pairingService = pairingService
    }

    @OnOpen
    void onOpen(String service, String pairingId, String endpoint, WebSocketSession session) {
        log.debug "New '$service' pairing session [pairingId: $pairingId] - endpoint: ${endpoint}"
        this.service = service
        this.pairingId = pairingId
        this.endpoint = endpoint
        this.session = session
        // acquire a pairing
        final resp = this.pairingService.acquirePairingKey(service, endpoint)
        session.sendAsync(new PairingSend<>(UUID.randomUUID().toString(), resp))
    }

    @OnMessage
    void onMessage(PairingMessage message) {
        log.debug "Receiving '$service' message=$message [pairingId: $pairingId]"
        if( message instanceof PairingReply ) {
            listener.onReply(service, pairingId, message)
        }
    }

    @OnClose
    void onClose() {
        log.debug "Closed '$service' pairing session [pairingId: $pairingId]"
        listener.close(service, pairingId)
    }

}
