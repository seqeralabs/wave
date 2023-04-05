package io.seqera.wave.service.pairing.socket


import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
    private PairingInboundStore inbound

    @Inject
    private PairingOutboundQueue outbound

    /**
     * Registers a consumer for a given service, endpoint, consumer ID, and pairing message consumer.
     *
     * @param service the name of the service to register the consumer for
     * @param endpoint the endpoint to register the consumer for
     * @param consumer the pairing message consumer to be registered
     */
    void registerConsumer(String service, String endpoint, String sessionId, Consumer<PairingMessage> consumer) {
        final streamKey = buildStreamKey(service, endpoint)
        outbound.registerConsumer(streamKey, sessionId, consumer)
    }

    /**
     * De-register a consumer with a given service, endpoint, and consumer ID.
     *
     * @param service the service to deregister the consumer from
     * @param endpoint the endpoint to deregister the consumer from
     */
    void deregisterConsumer(String service, String endpoint, String sessionId) {
        final streamKey = buildStreamKey(service, endpoint)
        outbound.unregisterConsumer(streamKey, sessionId)
    }

    /**
     * Determines whether the channel can handle messages for the given service and endpoint.
     *
     * @param service the name of the service to check for
     * @param endpoint the endpoint to check for
     * @return true if the message stream has a consumer for the given service and endpoint, false otherwise
     */
    boolean canHandle(String service, String endpoint) {
        final streamKey = buildStreamKey(service, endpoint)
        return outbound.hasConsumer(streamKey)
    }

    /**
     * Sends a message request to a given service and endpoint.
     *
     * @param service the name of the service to send the request to
     * @param endpoint the endpoint to send the request to
     * @param message the message to send
     * @param <M> the type of the message being sent
     * @param <R> the type of the response expected
     * @return a future containing the response to the request
     */
    public <M extends PairingMessage, R extends PairingMessage> CompletableFuture<R> sendRequest(String service, String endpoint, M message) {

        // create a unique Id to identify this command
        final result = inbound .create(message.msgId)
        // send message to the stream
        final streamKey = buildStreamKey(service, endpoint)
        log.trace "Sending message '${message}' to stream '${streamKey}'"
        outbound.sendMessage(streamKey, message)

        // return the future to the caller
        return (CompletableFuture<R>) result
    }

    /**
     * Receives a pairing message response from a given service and endpoint and completes
     * the future associated with the message's msgId with the response message.
     *
     * @param service the name of the service sending the response
     * @param endpoint the endpoint the response is sent to
     * @param message the pairing message response received
     */
    void receiveResponse(PairingMessage message) {
        inbound.complete(message.msgId, message)
    }

    private static String buildStreamKey(String service, String endpoint) {
        return "${service}:${endpoint}".toString()
    }

}
