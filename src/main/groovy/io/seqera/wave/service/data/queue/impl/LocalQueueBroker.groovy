package io.seqera.wave.service.data.queue.impl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.data.queue.MessageBroker
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Primary
@Singleton
@CompileStatic
class LocalQueueBroker implements MessageBroker<String> {

    @Value('${wave.queue.poolTimeout:5s}')
    private Duration timeout

    private ConcurrentHashMap<String, QueueSpooler> store = new ConcurrentHashMap<>()

    class QueueSpooler implements Runnable {
        final String key
        final Consumer<String> consumer
        final LinkedBlockingQueue<String> queue
        final Thread thread

        QueueSpooler(String key, Consumer<String> consumer) {
            log.debug "Creating spooler thread for queue: $key"
            this.key = key
            this.consumer = consumer
            this.queue = new LinkedBlockingQueue<>()
            this.thread = new Thread(this)
            this.thread.start()
        }

        @Override
        void run() {
            while( !thread.isInterrupted() ) {
                try {
                    final value = queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    if( value!= null )
                        consumer.accept(value)
                }
                catch (InterruptedException e) {
                    log.debug "Interrupting spooler thread for queue: $key"
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        boolean offer(String message) {
            queue.offer(message)
        }

        void interrupt() {
            thread.interrupt()
        }
    }

    @Override
    void sendMessage(String streamKey, String message) {
        store.get(streamKey).offer(message)
    }

    @Override
    void registerConsumer(String streamKey, Consumer<String> consumer) {
        store.computeIfAbsent(streamKey, (key)-> new QueueSpooler(key,consumer))
    }

    @Override
    void unregisterConsumer(String streamKey) {
        final spooler = store.remove(streamKey)
        if( spooler ) {
            spooler.interrupt()
        }
        else {
            log.warn "Unknown spooler for queue key: $streamKey"
        }
    }

    @Override
    boolean hasConsumer(String streamKey) {
        return store.containsKey(streamKey)
    }
}
