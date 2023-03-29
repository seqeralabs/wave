package io.seqera.wave.service.data.stream

import java.util.function.Consumer

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.util.TypeHelper

@CompileStatic
abstract class AbstractMessageStream<M> {

    private MessageBroker<String> broker
    private EncodingStrategy<M> encodingStrategy

    AbstractMessageStream(MessageBroker<String> broker) {
        final type = TypeHelper.getGenericType(this, 0)
        this.encodingStrategy = new MoshiEncodeStrategy<M>(type) {}
        this.broker = broker
    }

    protected abstract String getPrefix()

    protected String key0(String k) { return getPrefix() + k }

    void sendMessage(String topic, M message) {
        final encodedMessage = encodingStrategy.encode(message)
        final key = key0(topic)
        broker.sendMessage(key, encodedMessage)
    }

    void registerConsumer(String topic, String consumerId, Consumer<M> consumer) {
        final key = key0(topic)
        broker.registerConsumer(key, consumerId, message -> {
            final decodeMessage = encodingStrategy.decode(message)
            consumer.accept(decodeMessage)
        })
    }

    void unregisterConsumer(String topic, String consumerId) {
        final key = key0(topic)
        broker.unregisterConsumer(key, consumerId)
    }

    boolean hasConsumer(String topic) {
        final key = key0(topic)
        return broker.hasConsumer(key)
    }
}
