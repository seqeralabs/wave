package io.seqera.wave.service.data.queue.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.queue.ConsumerGroup
import io.seqera.wave.service.data.queue.QueueBroker
import jakarta.inject.Inject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub

/**
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
    init(ConsumerGroup<String> localConsumers) {
        this.localConsumers = localConsumers

        if( subscriber )
            return

        final groupName = localConsumers.group()
        this.subscriber = new JedisPubSub() {
            @Override
            void onMessage(String channel, String queueKey) {
                if( channel != groupName )
                    return

                log.debug "Receiving redis message on group='$groupName'; queue=$queueKey"
                if( !localConsumers.canConsume(queueKey) ) {
                    log.debug "No local consumers for queue=$queueKey"
                    return
                }

                // Get the request
                try(Jedis conn=pool.getResource()) {
                    final element = conn.brpop(1.0 as double, queueKey)
                    if( !element )
                        return

                    localConsumers.consume(queueKey, element.element)
                }

            }}

        // subscribe redis events
        final name = "redis-pairing-queue-subscriber"
        Thread.startDaemon(name) {
            try(Jedis conn=pool.getResource()) {
                conn.subscribe(subscriber, groupName)
            }
        }

    }

    @Override
    def send(String queueKey, String message) {
        try(Jedis conn=pool.getResource()) {

            // Add new request to the left of the queue
            conn.lpush(queueKey, message)

            // Notify that there is a new request on that queue
            conn.publish(localConsumers.group(), queueKey)
        }
    }
}
