package io.seqera.wave.service.data.queue.impl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.data.queue.MessageBroker
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'redis')
@Primary
@Singleton
@CompileStatic
class RedisQueueBroker implements MessageBroker<String>  {

    @Value('${wave.queue.poolTimeout:5s}')
    private Duration timeout

    @Inject
    private JedisPool pool

    private ConcurrentHashMap<String,QueueSpooler> store = new ConcurrentHashMap<>()

    class QueueSpooler implements Runnable {
        final String key
        final Consumer<String> consumer
        final Thread thread

        QueueSpooler(String key, Consumer<String> consumer) {
            log.debug "Creating spooler thread for queue: $key"
            this.key = key
            this.consumer = consumer
            this.thread = new Thread(this)
            this.thread.start()
        }

        private String poll(Duration duration) {
            try (Jedis conn = pool.getResource()) {
                final result = conn.brpop(duration.toSeconds() as int , key)
                if( !result )
                    return null
                if( result && result.size()==2 ) {
                    log.trace "Received queue message=$result"
                    return result[1]
                }
                else {
                    log.error "Unexpected length for queue message=$result"
                    return result[1]
                }
            }
        }

        @Override
        void run() {
            while( !thread.isInterrupted() ) {
                try {
                    final value = poll(timeout)
                    if( value!= null ) {
                        consumer.accept(value)
                    }
                }
                catch (InterruptedException e) {
                    log.debug "Interrupting spooler thread for queue: $key"
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        boolean offer(String message) {
            try (Jedis conn = pool.getResource()) {
                conn.lpush(key, message)
            }
        }

        void close() {
            // interrupt the thread
            thread.interrupt()
            try {
                thread.join(timeout.toMillis())
            }
            catch (Exception e) {
                log.debug "Unexpected exception while waiting spooler thread $key - cause: ${e.message}"
            }
            // clean up the redis queue
            try (Jedis conn = pool.getResource()) {
                conn.del(key)
            }
            catch (Exception e) {
                log.debug "Unexpected exception while deleting queue $key - cause: ${e.message}"
            }
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
            spooler.close()
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
