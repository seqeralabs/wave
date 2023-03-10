package io.seqera.wave.service.data.queue

/**
 * Defines a group of consumers that can consume objects of type V
 * from a queue
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @param <V> Type of object consumed
 */
interface ConsumerGroup<V> {

    /**
     * Key to identify this group of consumers
     *
     * @return the group identifier
     */
    String group()

    /**
     * Check if this group can consume from a given queue
     *
     * @param queueKey Queue identifier
     * @return whether this queueKey can be consumed
     */
    boolean canConsume(String queueKey)

    /**
     * Pass a message to the consume group to be consumed
     * by only one consumer
     *
     * @param queueKey Source queue identifier
     * @param message Value to consume
     */
    void consume(String queueKey, V message)

}
