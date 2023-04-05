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

    final private ConcurrentHashMap<String,MessageSpooler> spooler = new ConcurrentHashMap<>()

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

    void registerConsumer(String streamKey, String sessionId, Consumer<M> consumer) {
        synchronized (spooler) {
            final k = key0(streamKey)
            def instance = spooler.get(k)
            if( instance == null ) {
                instance = new MessageSpooler(k, broker, true)
                spooler.put(k, instance)
            }
            // add the consumer
            instance.addConsumer(sessionId, (String message) -> {
                final decodeMessage = encoder.decode(message)
                consumer.accept(decodeMessage)
            })
        }
    }

    void unregisterConsumer(String streamKey, String sessionId) {
        synchronized (spooler) {
            final k = key0(streamKey)
            final instance = spooler.remove(k)
            if( !instance ) {
                log.warn "Cannot find spooler instance for key: ${k}"
                return
            }
            final count = instance.removeConsumer(sessionId)
            // remember to switch off the light
            if( count==0 ) {
                instance.close()
                spooler.remove(k)
            }
        }
    }

    boolean hasConsumer(String streamKey) {
        final key = key0(streamKey)
        return spooler
                .computeIfAbsent(key, (it)-> new MessageSpooler(it,broker))
                .exists(key)
    }
}
