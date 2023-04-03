package io.seqera.wave.service.data.queue

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.util.TypeHelper
/**
 * Implements a distributed message queue in which many listeners can register
 * to consume a message. A message instance can be consumed by one and only listener.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @param <M>    The type of message that can be sent through the broker.
 */
@Slf4j
@CompileStatic
abstract class AbstractMessageQueue<M> {

    private MessageBroker<String> broker

    private EncodingStrategy<M> encoder

    private ConcurrentHashMap<String,MessageSpooler> spooler = new ConcurrentHashMap<>()

    AbstractMessageQueue(MessageBroker<String> broker) {
        final type = TypeHelper.getGenericType(this, 0)
        this.encoder = new MoshiEncodeStrategy<M>(type) {}
        this.broker = broker
    }

    protected abstract String getPrefix()

    protected String key0(String k) { return getPrefix() + k }

    void sendMessage(String streamKey, M message) {
        final encodedMessage = encoder.encode(message)
        spooler
                .computeIfAbsent(key0(streamKey), (it)-> new MessageSpooler(it,broker))
                .offer(encodedMessage)
    }

    void registerConsumer(String streamKey, Consumer<M> consumer) {
        spooler.computeIfAbsent(key0(streamKey), (it)-> new MessageSpooler(it,broker,(String message) -> {
            final decodeMessage = encoder.decode(message)
            consumer.accept(decodeMessage)
        }))
    }

    void unregisterConsumer(String streamKey) {
        spooler.remove(key0(streamKey))?.close()
    }

    boolean hasConsumer(String streamKey) {
        return spooler.get(key0(streamKey))?.exists(streamKey)
    }
}
