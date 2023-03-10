package io.seqera.wave.tower.client

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.pairing.socket.msg.ProxyGetRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyGetResponse
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Inject

@Slf4j
@CompileStatic
class TowerClientSocket extends TowerClient {
    private static final String SERVICE = "tower"

    @Inject
    private PairingChannel channel

    boolean isEndpointRegistered(String endpoint) {
        return channel.isEndpointRegistered(SERVICE, endpoint)
    }

    protected <T> CompletableFuture<T> getAsync(URI uri, String towerEndpoint, String authorization, Class<T> type) {
        final CompletableFuture<ProxyGetResponse> result = channel.sendServiceRequest(
                SERVICE,
                towerEndpoint,
                new ProxyGetRequest(msgId: UUID.randomUUID(), uri: uri, bearerAuth: authorization))
        return result
                .thenCompose { resp ->
                    log.trace "Tower response for request GET '${uri}' => ${resp.status}"
                    switch (resp.status.code) {
                        case 200:
                            return CompletableFuture.completedFuture(JacksonHelper.fromJson(resp.body, type))
                        case 401:
                            throw new HttpResponseException(401, "Unauthorized access to Tower resource: $uri", resp.body)
                        case 404:
                            final msg = "Tower resource not found: $uri"
                            throw new HttpResponseException(404, msg, resp.body)
                        default:
                            def body = resp.body
                            def msg = "Unexpected status code ${resp.status} while accessing Tower resource: $uri"
                            if (body)
                                msg += " - response: ${body}"
                            throw new HttpResponseException(resp.status, msg)
                    }
                }
                .exceptionally { err ->
                    throw handleIoError(err, uri)
                }
    }

}
