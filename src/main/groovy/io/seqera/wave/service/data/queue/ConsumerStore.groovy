package io.seqera.wave.service.data.queue

interface ConsumerStore<V> {

    String group()

    boolean canConsume(String queueKey)

    void consume(String queueKey, V message)

}
