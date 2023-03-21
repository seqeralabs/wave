package io.seqera.wave.service.data.queue.impl

import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.Retryable
import io.seqera.wave.service.data.queue.ConsumerGroup
import io.seqera.wave.service.data.queue.QueueBroker
import jakarta.inject.Inject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.exceptions.JedisConnectionException

/**
 * Distributed message broker that forwards all incoming messages to a
 * single consumer that is listening on any instance.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Requires(env='redis')
@Prototype
@CompileStatic
class RedisQueueBroker implements QueueBroker<String> {

    @Inject
    private JedisPool pool

    private JedisPubSub subscriber

    private ConsumerGroup<String> localConsumers

    @Override
    void init(ConsumerGroup<String> localConsumers) {
        this.localConsumers = localConsumers

        if( subscriber )
            return

        final groupName = localConsumers.group()
        this.subscriber = new JedisPubSub() {

            @Override
            void onMessage(String channel, String queueKey) {

                // When there is new request available for this group of
                // consumers a message is send to the group topic with the
                // value of the queue that holds the new request
                if( channel != groupName )
                    return

                log.debug "Receiving redis message on group='$groupName'; queue=$queueKey"
                if( !localConsumers.canConsume(queueKey) ) {
                    log.debug "No local consumers for queue=$queueKey"
                    return
                }

                // Pop right value from the queue list
                try(Jedis conn=pool.getResource()) {

                    // Use a timeout because only one instance can consume this value
                    final element = conn.brpop(10.0 as double, queueKey)
                    if( !element )
                        return

                    // Pass request to one local consumer
                    localConsumers.consume(queueKey, element.element)
                }

            }}

        // subscribe redis events
        final name = "redis-pairing-queue-subscriber"
        Thread.startDaemon(name) {
            subscribe(name)
        }
    }

    @Retryable(includes=[JedisConnectionException])
    void subscribe(String name) {
        try (Jedis conn = pool.getResource()) {
            log.debug "Redis connection for '${name}' connected=${conn.isConnected()} and broken=${conn.isBroken()}"
            conn.subscribe(subscriber, localConsumers.group())
        }
    }

    @Override
    void send(String queueKey, String message) {
        try(Jedis conn=pool.getResource()) {

            // Add new request to the left of the queue
            conn.lpush(queueKey, message)

            // Notify local consumers topic that there is a
            // new request at the given queue key
            conn.publish(localConsumers.group(), queueKey)
        }
    }

    @Override
    void consume(String queueKey, Consumer<String> consumer) {
        try(Jedis conn=pool.getResource()) {
            // Consume all pending messages
            while (true) {
                final element = conn.brpop(1.0 as double, queueKey)
                if (!element)
                    return
                consumer.accept(element.element)
            }
        }
    }

    @Override
    void close() {
        try {
            subscriber.unsubscribe()
        }
        catch (Throwable e) {
            log.warn "Unexpected error while unsubscribing redis topic '${localConsumers.group()}' - cause: ${e.message}"
        }

        try( Jedis conn=pool.getResource() ) {
            conn.flushAll()
        }
    }
}
