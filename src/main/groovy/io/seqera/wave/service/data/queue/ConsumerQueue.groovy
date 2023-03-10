package io.seqera.wave.service.data.queue

import java.util.function.Consumer

interface ConsumerQueue<V> {

    send(String queueKey, V request)

    String addConsumer(String queueKey, Consumer<V> consumer)

    removeConsumer(String queueKey, String consumerId)
}
