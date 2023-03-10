package io.seqera.wave.service.data.queue

interface QueueBroker<V> {

    init(ConsumerStore<V> localConsumers)

    send(String queueKey, V message)

}
