package io.seqera.wave.service.data.queue

import java.util.function.Consumer

/**
 * Defines an interface that allows to send messages to a queue
 * and to attach consumers to a specific queue
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @param <V> Type of objects that the queue can manage
 */
interface ConsumerQueue<V> {

    /**
     * Send message to a queue
     *
     * @param queueKey Queue identifier
     * @param message Message to send
     */
    void send(String queueKey, V message)

    /**
     * Add a consumer listening to a given queue
     *
     * @param queueKey Queue identifier
     * @param consumer Message consumer
     * @return Consumer identifier, useful to remove it
     */
    String addConsumer(String queueKey, Consumer<V> consumer)

    /**
     * Remove a consumer listener
     *
     * @param queueKey Queue identifier
     * @param consumerId Consumer identifier
     */
    void removeConsumer(String queueKey, String consumerId)
}
