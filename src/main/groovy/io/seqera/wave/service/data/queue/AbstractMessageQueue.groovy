package io.seqera.wave.service.data.queue

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.pairing.socket.MessageSender
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

    void sendMessage(String target, M message) {
        final encodedMessage = encoder.encode(message)
        spooler
                .computeIfAbsent(key0(target), (it)-> new MessageSpooler(it,broker))
                .offer(encodedMessage)
    }

    void registerClient(String target, String clientId, MessageSender<M> sender) {
        synchronized (spooler) {
            final k = key0(target)
            def instance = spooler.get(k)
            if( instance == null ) {
                instance = new MessageSpooler(k, broker, true)
                spooler.put(k, instance)
            }
            // add the client
            instance.addClient(clientId, (String message) -> {
                final decodeMessage = encoder.decode(message)
                sender.sendAsync(decodeMessage)
            })
        }
    }

    void unregisterClient(String target, String clientId) {
        synchronized (spooler) {
            final k = key0(target)
            final instance = spooler.remove(k)
            if( !instance ) {
                log.warn "Cannot find spooler instance for key: ${k}"
                return
            }
            final count = instance.removeClient(clientId)
            // remember to switch off the light
            if( count==0 ) {
                instance.close()
                spooler.remove(k)
            }
        }
    }

    boolean hasClient(String target) {
        final key = key0(target)
        return spooler
                .computeIfAbsent(key, (it)-> new MessageSpooler(it,broker))
                .exists(key)
    }
}
