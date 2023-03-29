package io.seqera.wave.service.pairing.socket

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
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
    private PairingStream messageStream

    @Value('${wave.pairing.channel.timeout:5s}')
    private Duration timeout

    void registerConsumer(String service, String endpoint, String consumerId, Consumer<PairingMessage> consumer) {
        final topic = buildTopic(service, endpoint)
        messageStream.registerConsumer(topic, consumerId, consumer)
    }

    void deregisterConsumer(String service, String endpoint, String consumerId) {
        final topic = buildTopic(service, endpoint)
        messageStream.unregisterConsumer(topic, consumerId)
    }

    boolean canConsume(String service, String endpoint) {
        final topic = buildTopic(service, endpoint)
        return messageStream.hasConsumer(topic)
    }

    public <M extends PairingMessage, R extends PairingMessage> CompletableFuture<R> sendRequest(String service, String endpoint, M message) {
        log.debug "Request message=${message.class.simpleName} to endpoint='$endpoint'"

        // create a unique Id to identify this command
        final result = futuresStore
                .create(message.msgId)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)

        // send message to the stream
        final topic = buildTopic(service, endpoint)
        log.debug "Sending message '${message.msgId}' to stream '${topic}'"
        messageStream.sendMessage(topic, message)

        // return the future to the caller
        return (CompletableFuture<R>) result
    }

    void receiveResponse(String service, String endpoint, PairingMessage message) {
        futuresStore.complete(message.msgId, message)
    }

    private static String buildTopic(String service, String endpoint) {
        return "${service}:${endpoint}".toString()
    }

}
