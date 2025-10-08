/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.pairing.socket

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.seqera.wave.service.license.LicenseManClient
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.service.pairing.socket.msg.PairingResponse
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.random.LongRndKey.rndHex
/**
 * Implements Wave pairing websocket server
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
@ExecuteOn(TaskExecutors.BLOCKING)
@ServerWebSocket("/pairing/{service}/token/{token}{?endpoint}")
class PairingWebSocket {

    @Inject
    private PairingChannel channel

    @Inject
    private PairingService pairingService

    @Inject
    @Nullable
    private LicenseManClient licenseManager

    @Value('${wave.closeSessionOnInvalidLicenseToken:false}')
    private boolean closeSessionOnInvalidLicenseToken

    @Nullable
    @Value('${wave.denyHosts}')
    private List<String> denyHosts

    @OnOpen
    void onOpen(String service, String token, String endpoint, WebSocketSession session) {
        log.debug "Opening pairing session - endpoint: ${endpoint} [sessionId: $session.id]"

        if( isDenyHost(endpoint) ) {
            log.warn "Pairing not allowed for endpoint: ${endpoint}"
            session.close(CloseReason.POLICY_VIOLATION)
            return
        }

        // check for a valid connection token
        if( licenseManager && !isLicenseTokenValid(token, endpoint) && closeSessionOnInvalidLicenseToken ) {
            session.close(CloseReason.POLICY_VIOLATION)
            return
        }

        // Register the client and the sender callback that it's needed to deliver
        // the message to the remote client
        channel.registerClient(service, endpoint, session.id,(msg) -> {
            log.trace "Sending message=${msg} - endpoint: ${endpoint} [sessionId: $session.id]"
            session
                .sendAsync(msg)
                .exceptionally(ex-> log.error("Failed to send message=${msg} - endpoint: ${endpoint} [sessionId: $session.id]"))
        })

        // acquire a pairing key and send it to the remote client
        final resp = this.pairingService.acquirePairingKey(service, endpoint)
        final msg = new PairingResponse(
                msgId: rndHex(),
                pairingId: resp.pairingId,
                publicKey: resp.publicKey )
        session
            .sendAsync(msg)
            .exceptionally(ex-> log.error("Failed to send message=${msg} - endpoint: ${endpoint} [sessionId: $session.id]"))
    }

    @OnMessage
    void onMessage(String service, String token, String endpoint, PairingMessage message, WebSocketSession session) {
        if( message instanceof PairingHeartbeat ) {
            log.trace "Receiving heartbeat - endpoint: ${endpoint} [sessionId: $session.id]"
            // send pong message
            final msg = new PairingHeartbeat(msgId:rndHex())
            session
                .sendAsync(msg)
                .exceptionally(ex-> log.error("Failed to send message=${msg} - endpoint: ${endpoint} [sessionId: $session.id]"))

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

    protected boolean isLicenseTokenValid(String token, String endpoint) {
        try {
            final resp = licenseManager.checkToken(token, "tower-enterprise")
            if( !resp ) {
                log.warn "Failed license token validation for endpoint: ${endpoint}; token: $token"
                return false
            }

            if( resp.expiration.isBefore(Instant.now()) ) {
                // just warn about the license expiration - due not return return false
                // to prevent the interruption of the service
                log.warn "Expired license for customer with endpoint: ${endpoint}; token: ${token}"
            }
            else {
                log.info "Validated license for customer with endpoint: ${endpoint}; token: ${token}"
            }
            return true
        }
        catch (HttpClientResponseException e) {
            log.error "Failed license token validation - Unexpected response for endpoint: ${endpoint}; token: ${token}; status: ${e.status.code}; message: ${e.message}"
        }
        catch (Throwable e) {
            log.error "Failed license token validation - Unexpected exception for endpoint: ${endpoint}; token: ${token} - cause: ${e.message}", e
        }
        // return 'true' when it's not possible to verify the license due to expected errors
        // to prevent a valid license is rejected due to transitory failures
        return true
    }

    protected boolean isDenyHost(String endpoint) {
        for( String it : (denyHosts ?: List.of()) ) {
            if( endpoint.contains(it) )
                return true
        }
        return false
    }
}
