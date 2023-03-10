package io.seqera.wave.service.data.queue

/**
 * Define the interface to send and receive objects to a queue
 * using a broker
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @param <V> Type of objects that the broker can manage
 */
interface QueueBroker<V> {

    /**
     * Initialize the broker to notify localConsumers of new messages available
     * on any queue
     *
     * @param localConsumers Consumers on the local instance
     */
    void init(ConsumerGroup<V> localConsumers)

    /**
     * Send a message to a given queue
     *
     * @param queueKey Queue identifier
     * @param message Value of type V to send
     */
    void send(String queueKey, V message)

}
