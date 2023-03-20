package io.seqera.wave.service.pairing.socket

import java.time.Duration

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

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Prototype  // note use prototype to have an instance for each session
@ServerWebSocket("/pairing/{service}/token/{token}{?endpoint}")
class PairingSocket {
    public static final CloseReason INVALID_ENDPOINT_REGISTER = new CloseReason(4000, "Invalid endpoint register")

    private WebSocketSession session
    private String service
    private String endpoint
    private String consumerId

    private PairingChannel channel
    private PairingService pairingService
    private TaskScheduler scheduler

    PairingSocket(PairingChannel channel, PairingService pairingService, TaskScheduler scheduler) {
        this.channel = channel
        this.pairingService = pairingService
        this.scheduler = scheduler
    }

    @OnOpen
    void onOpen(String service, String token, String endpoint, WebSocketSession session) {
        log.debug "New '$service' pairing session - endpoint: ${endpoint}"
        this.service = service
        this.endpoint = endpoint
        this.session = session

        // Register endpoint
        if( !channel.registerEndpoint(service, endpoint, token) ) {
            log.debug "Invalid registration. Close session service=$service endpoint=$endpoint"
            scheduler.schedule(Duration.ofMillis(100), {

                session.close(INVALID_ENDPOINT_REGISTER)
            })
            return
        }

        // acquire a pairing
        final resp = this.pairingService.acquirePairingKey(service, endpoint)
        session.sendAsync(new PairingResponse(
                msgId: UUID.randomUUID(),
                pairingId: resp.pairingId,
                publicKey: resp.publicKey
        ))

        // Register on send consumer
        consumerId = channel.onServiceRequestListener(service, endpoint, {
            session.sendSync(it)
        })

    }

    @OnMessage
    void onMessage(PairingMessage message) {
        log.debug "Receiving '$service' message=$message [sessionId: $session.id]"
        if( message instanceof PairingHeartbeat )
            return

        channel.receiveServiceResponse(service, endpoint, message)
    }

    @OnClose
    void onClose() {
        log.debug "Closed '$service' pairing session [sessionId: $session.id]"
        if( consumerId )
            channel.removeListener(service, endpoint, consumerId)
    }

}
