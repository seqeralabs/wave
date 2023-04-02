package io.seqera.wave.service.data.queue

import java.util.function.Consumer

/**
 * Interface for a message broker that can send messages to different streams and register/unregister consumers for those streams.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @param <M>    The type of message that can be sent through the broker.
 */
interface MessageBroker<M> {

    /**
     * Sends a message to the specified stream.
     *
     * @param streamKey The unique key for the stream.
     * @param message The message to send.
     */
    void sendMessage(String streamKey, M message)


    /**
     * Registers a consumer for the specified stream and consumer ID.
     *
     * @param streamKey The unique key for the stream.
     * @param messageConsumer The consumer that will receive messages from the stream.
     */
    void registerConsumer(String streamKey, Consumer<M> messageConsumer)

    /**
     *
     * Unregisters a consumer for the specified stream and consumer ID.
     *
     * @param streamKey The unique key for the stream.
     *
     */
    void unregisterConsumer(String streamKey)

    /**
     * Checks if the specified stream has any registered consumers.
     *
     * @param streamKey The unique key for the stream.
     * @return true if there is at least one registered consumer for the stream, false otherwise.
     */
    boolean hasConsumer(String streamKey)
}




