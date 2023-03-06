package io.seqera.wave.service.data
/**
 * Define the interface for publishing {@link java.util.concurrent.CompletableFuture} values.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FuturePublisher<T> {

    /**
     * Subscribe the {@link FutureListener}
     *
     * @param listener
     *      The {@link FutureListener} instance that will handle the receiving of the a published message
     */
    void subscribe(FutureListener<T> listener)

    /**
     * Publish a
     * @param entry
     */
    void publish(T entry)

    /**
     * Close the publisher
     */
    default void close() { }
}
