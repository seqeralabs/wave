package io.seqera.wave.service.data.queue.impl

import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.Retryable
import io.seqera.wave.service.data.queue.ConsumerTopic
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

    private ConsumerTopic<String> localConsumers

    @Override
    void init(ConsumerTopic<String> localConsumers) {
        this.localConsumers = localConsumers

        if( subscriber )
            return

        final topic = localConsumers.topic()
        this.subscriber = new JedisPubSub() {

            @Override
            void onMessage(String channel, String queueKey) {
                // When there is new request available for this topic of
                // consumers a message is send to the group topic with the
                // value of the queue that holds the new request
                if( channel != topic )
                    return

                log.debug "Receiving redis message on topic='$topic'; queue=$queueKey"
                if( !localConsumers.canConsume(queueKey) ) {
                    log.debug "No local consumers for queue=$queueKey"
                    return
                }

                // Consume all messages available at the queue
                consume(queueKey, msg -> localConsumers.consume(queueKey, msg))

            }}

        // subscribe redis events
        final name = "redis-pairing-queue-subscriber"
        Thread.startDaemon(name,() -> subscribe(name))
    }

    @Retryable(includes=[JedisConnectionException])
    void subscribe(String name) {
        try (Jedis conn = pool.getResource()) {
            log.debug "Redis connection for '${name}' connected=${conn.isConnected()} and broken=${conn.isBroken()}"
            conn.subscribe(subscriber, localConsumers.topic())
        }
    }

    @Override
    void send(String queueKey, String message) {
        try(Jedis conn=pool.getResource()) {

            // Add new request to the left of the queue
            conn.lpush(queueKey, message)

            // Notify local consumers topic that there is a
            // new request at the given queue key
            conn.publish(localConsumers.topic(), queueKey)
        }
    }

    @Override
    void consume(String queueKey, Consumer<String> consumer) {
        try(Jedis conn=pool.getResource()) {
            // Consume all pending messages
            while (true) {

                // Pop an element from the right side of the queue with a timeout of one second
                final entry = conn.brpop(1, queueKey)

                // Redis BRPOP is an atomic operation, so only one instance of Wave will get the
                // queue element. If it returns a null element it means that the timeout was
                // reached and another Wave instance picked up the element.
                if (!entry)
                    return
                // the first element is the queue key
                // the second element is the actual value received
                if (entry.size()!=2)
                    throw new IllegalStateException("Unexpected number elements: $entry")
                // Consume the element
                consumer.accept(entry.get(1))
            }
        }
    }

    @Override
    void close() {
        try {
            subscriber.unsubscribe()
        }
        catch (Throwable e) {
            log.warn "Unexpected error while unsubscribing redis topic '${localConsumers.topic()}' - cause: ${e.message}"
        }

        try( Jedis conn=pool.getResource() ) {
            conn.flushAll()
        }
    }
}
