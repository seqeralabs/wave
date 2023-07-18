package io.seqera.wave.service.pairing.socket

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpStatus
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.seqera.wave.service.license.LicenseManServiceClient
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import jakarta.inject.Singleton
import static io.seqera.wave.util.LongRndKey.rndHex
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

    private final PairingChannel channel
    private final PairingService pairingService
    private final LicenseManServiceClient licenseManServiceClient


    PairingWebSocket(PairingChannel channel, PairingService pairingService, LicenseManServiceClient licenseManServiceClient) {
        this.channel = channel
        this.pairingService = pairingService
        this.licenseManServiceClient = licenseManServiceClient
    }

    @OnOpen
    void onOpen(String service, String token, String endpoint, WebSocketSession session) {
        log.debug "Opening pairing session - endpoint: ${endpoint} [sessionId: $session.id]"
        if(licenseManServiceClient.checkToken(token,null).status.code != HttpStatus.OK.code){
            log.debug "license is not valid, closing the session"
            session.close()
            return
        }
        // Register the client and the sender callback that it's needed to deliver
        // the message to the remote client
        channel.registerClient(service, endpoint, session.id,(pairingMessage) -> {
            log.trace "Websocket send message id=$pairingMessage.msgId"
            session .sendAsync(pairingMessage)
        })

        // acquire a pairing key and send it to the remote client
        final resp = this.pairingService.acquirePairingKey(service, endpoint)
        session.sendAsync(new PairingResponse(
                msgId: rndHex(),
                pairingId: resp.pairingId,
                publicKey: resp.publicKey
        ))
    }

    @OnMessage
    void onMessage(String service, String token, String endpoint, PairingMessage message, WebSocketSession session) {
        if( message instanceof PairingHeartbeat ) {
            log.trace "Receiving heartbeat - endpoint: ${endpoint} [sessionId: $session.id]"
            // send pong message
            final pong = new PairingHeartbeat(msgId:rndHex())
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
