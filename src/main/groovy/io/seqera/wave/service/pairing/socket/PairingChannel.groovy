package io.seqera.wave.service.pairing.socket

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Handle sending and replies for pairing messages
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingChannel {

    @Inject
    private PairingFutureStore futuresStore

    @Inject
    private PairingSendQueue sendQueue

    @Inject
    private PairingEndpointsStore endpoints

    @Value('${wave.pairing.future.timeout:5s}')
    private Duration timeout


    boolean registerEndpoint(String service, String endpoint, String token) {
        final key = buildKey(service, endpoint)
        final value = endpoints.get(key)
        if( value == null ) {
            log.debug "New $service endpoint '$endpoint' registered"
            endpoints.put(key, token)
        }
        return value == token
    }

    boolean isEndpointRegistered(String service, String endpoint) {
        final key = buildKey(service, endpoint)
        return endpoints.get(key) != null
    }

    public <M extends PairingMessage, R extends PairingMessage> CompletableFuture<R> sendServiceRequest(String service, String endpoint, M message) {
        log.debug "Request message=${message.class.simpleName} to endpoint='$endpoint'"

        // create a unique Id to identify this command
        final result = futuresStore.create(message.msgId)

        // publish
        final queue = buildKey(service, endpoint)
        sendQueue.send(queue, message)

        // return the future to the caller
        return (CompletableFuture<R>) result
    }


    void receiveServiceResponse(String service, String endpoint, PairingMessage message) {
        futuresStore.complete(message.msgId, message)
    }

    String onServiceRequestListener(String service, String endpoint, Consumer<PairingMessage> consumer) {
        log.debug "Register consumer on send service='$service' endpoint='$endpoint'"
        final queue = buildKey(service, endpoint)
        return sendQueue.addConsumer(queue, consumer)
    }

    void removeListener(String service, String endpoint, String consumerId) {
        final queue = buildKey(service, endpoint)
        sendQueue.removeConsumer(queue, consumerId)
    }

    private String buildKey(String service, String endpoint) {
        return "${service}#${endpoint}"
    }
}
