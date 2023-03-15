package io.seqera.wave.tower.client.service

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import jakarta.inject.Inject

@Slf4j
@CompileStatic
class SocketServiceClient extends AbstractServiceClient {

    @Inject
    private PairingChannel channel

    boolean isEndpointRegistered(String endpoint) {
        return channel.isEndpointRegistered(PairingService.TOWER_SERVICE, endpoint)
    }

    @Override
    CompletableFuture<ProxyHttpResponse> sendAsync(String service, String endpoint, ProxyHttpRequest request) {
        return channel.sendServiceRequest(service, endpoint, request)
    }
}
