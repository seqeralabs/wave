package io.seqera.wave.tower.client.connector

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import jakarta.inject.Inject
import static io.seqera.wave.service.pairing.PairingService.TOWER_SERVICE

/**
 * Implements a Tower connector using a WebSocket connection
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@CompileStatic
class WebSocketTowerConnector extends TowerConnector {

    @Inject
    private PairingChannel channel

    boolean isEndpointRegistered(String endpoint) {
        return channel.canHandle(TOWER_SERVICE, endpoint)
    }

    @Override
    CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request) {
        return channel
                .sendRequest(TOWER_SERVICE, endpoint, request)
    }

}
