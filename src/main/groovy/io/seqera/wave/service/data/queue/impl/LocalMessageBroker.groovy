package io.seqera.wave.service.data.queue.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.data.queue.MessageBroker
import jakarta.inject.Singleton

/**
 * An in-memory implementation of the {@link MessageBroker} interface.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Singleton
@CompileStatic
class LocalMessageBroker implements MessageBroker<String> {

    private final AtomicLong messageCounter = new AtomicLong(0)
    private final Map<String, Map<String, Consumer<String>>> consumers = new ConcurrentHashMap<>()

    @Override
    void sendMessage(String topic, String message) {
        final messageId = messageCounter.incrementAndGet()
        final topicConsumers = consumers.get(topic)
        if (topicConsumers == null || topicConsumers.isEmpty()) {
            throw new UnsupportedOperationException("no consumer listening on '$topic'")
        }

        final consumerId = topicConsumers.keySet()[(messageId % topicConsumers.size()) as int]
        Consumer<String> consumer = topicConsumers.get(consumerId)
        consumer.accept(message)
    }

    @Override
    void registerConsumer(String topic, String consumerId, Consumer<String> messageConsumer) {
        consumers.computeIfAbsent(topic, k -> new HashMap<>()).put(consumerId, messageConsumer)
    }

    @Override
    void unregisterConsumer(String topic, String consumerId) {
        if (!consumers.containsKey(topic)) throw new BadRequestException("no consumer listening on '$topic'")
        final topicConsumers = consumers.get(topic)
        if (topicConsumers.remove(consumerId) == null)
            throw new BadRequestException("consumer not registered")

    }

    @Override
    boolean hasConsumer(String topic) {
        return consumers.containsKey(topic) && !consumers.get(topic).isEmpty();
    }
}

