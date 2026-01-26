/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing.socket

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.data.queue.MessageSender
import io.seqera.service.pairing.socket.msg.PairingMessage
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
     * Registers a sender for a given service, endpoint, sender Id, and pairing message consumer.
     *
     * @param service the name of the service to register the consumer for
     * @param endpoint the endpoint to register the consumer for
     * @param sender the pairing message consumer to be registered
     */
    void registerClient(String service, String endpoint, String clientId, MessageSender<PairingMessage> sender) {
        final target = clientTarget(service, endpoint)
        outbound.registerClient(target, clientId, sender)
    }

    /**
     * De-register a consumer with a given service, endpoint, and consumer ID.
     *
     * @param service the service to deregister the consumer from
     * @param endpoint the endpoint to deregister the consumer from
     */
    void unregisterClient(String service, String endpoint, String clientId) {
        final target = clientTarget(service, endpoint)
        outbound.unregisterClient(target, clientId)
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
    <M extends PairingMessage, R extends PairingMessage> CompletableFuture<R> sendRequest(String service, String endpoint, M message) {

        // create a unique Id to identify this command
        final result = inbound .create(message.msgId)
        // send message to the stream
        final target = clientTarget(service, endpoint)
        outbound.offer(target, message)

        // return the future to the caller
        return (CompletableFuture<R>) result
    }

    /**
     * Receives a pairing message response from a given service and endpoint and completes
     * the future associated with the message's msgId with the response message.
     *
     * @param message the pairing message response received
     */
    void receiveResponse(PairingMessage message) {
        inbound.complete(message.msgId, message)
    }

    private static String clientTarget(String service, String uri) {
        final endpoint = uri.replace('http://','').replace('https://','')
        return "${service}:${endpoint}".toString()
    }

}
