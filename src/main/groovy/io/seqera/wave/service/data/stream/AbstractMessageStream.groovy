package io.seqera.wave.service.data.stream

import java.util.function.Consumer

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.util.TypeHelper

@CompileStatic
abstract class AbstractMessageStream<M> implements MessageBroker<M> {

    private MessageBroker<String> broker
    private EncodingStrategy<M> encodingStrategy

    AbstractMessageStream(MessageBroker<String> broker) {
        final type = TypeHelper.getGenericType(this, 0)
        this.encodingStrategy = new MoshiEncodeStrategy<M>(type) {}
        this.broker = broker
    }

    protected abstract String getPrefix()

    protected String key0(String k) { return getPrefix() + k }

    void sendMessage(String streamKey, M message) {
        final encodedMessage = encodingStrategy.encode(message)
        final key = key0(streamKey)
        broker.sendMessage(key, encodedMessage)
    }

    void registerConsumer(String streamKey, String consumerId, Consumer<M> consumer) {
        final key = key0(streamKey)
        broker.registerConsumer(key, consumerId, message -> {
            final decodeMessage = encodingStrategy.decode(message)
            consumer.accept(decodeMessage)
        })
    }

    void unregisterConsumer(String streamKey, String consumerId) {
        final key = key0(streamKey)
        broker.unregisterConsumer(key, consumerId)
    }

    boolean hasConsumer(String streamKey) {
        final key = key0(streamKey)
        return broker.hasConsumer(key)
    }
}
