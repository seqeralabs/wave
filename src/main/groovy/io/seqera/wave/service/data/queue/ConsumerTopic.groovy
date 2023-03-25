package io.seqera.wave.service.data.queue

/**
 * Defines a  of consumers that can consume objects of type V
 * from a queue
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @param <V> Type of object consumed
 */
interface ConsumerTopic<V> {

    /**
     * Key to identify this topic of consumers
     *
     * @return the topic identifier
     */
    String topic()

    /**
     * Check if this topic can consume from a given queue
     *
     * @param queueKey Queue identifier
     * @return whether this queueKey can be consumed
     */
    boolean canConsume(String queueKey)

    /**
     * Pass a message to the consumer topic to be consumed
     * by only one consumer
     *
     * @param queueKey Source queue identifier
     * @param message Value to consume
     */
    void consume(String queueKey, V message)

}
