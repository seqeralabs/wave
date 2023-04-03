package io.seqera.wave.service.data.queue

import java.time.Duration

/**
 * Interface for a message broker modelled as a blocking queue.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @author Paolo Di Tommmaso <paolo.ditommaso@gmail.com>
 * @param <M>    The type of message that can be sent through the broker.
 */
interface MessageBroker<M> {

    /**
     * Inserts the specified element at the tail of the specified queue.
     *
     * @param key
     *      The queue unique identified
     * @param value
     *  The value that should be added to the queue
     */
    void offer(String key, M value)

    /**
     * Retrieves and removes the head of this queue, waiting up to the specified wait time if necessary
     * for an element to become available.
     *
     * @param key
     *      The queue unique identifier
     * @param timeout
     *      How long to wait before giving up, in units of unit unit – a TimeUnit determining how to interpret the timeout parameter
     * @return
     *      The head of this queue, or null if the specified waiting time elapses before an element is available
     */
    M poll(String key, Duration timeout)

    /**
     * Remove the queue with the specified identifier
     *
     * @param key The queue unique identifier
     */
    void delete(String key)

    /**
     * Initialise the broker broker
     */
    void init(String key)

    /**
     * Check for the key existance
     *
     * @param key
     * @return
     */
    boolean exists(String key)
}




