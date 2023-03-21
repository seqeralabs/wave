package io.seqera.wave.service.data.future.impl

import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.Retryable
import io.seqera.wave.service.data.future.FutureListener
import io.seqera.wave.service.data.future.FuturePublisher
import jakarta.inject.Inject
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.exceptions.JedisConnectionException

/**
 * Implements a future store publisher based on Redis pub-sub.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Prototype
@Requires(env='redis')
@CompileStatic
class RedisFuturePublisher implements FuturePublisher<String> {

    private static final AtomicInteger count = new AtomicInteger()

    @Inject
    private JedisPool pool

    private String group

    private JedisPubSub subscriber

    @Override
    void subscribe(FutureListener<String> listener) {
        this.group = listener.group()
        if( !group )
            throw new IllegalArgumentException("Missing 'group' name for RedisFuturePublisher")

        // create the subscriber
        this.subscriber = new JedisPubSub() {
            @Override
            void onMessage(String channel, String message) {
                log.debug "Receiving redis message on group='$group'; message=$message"
                if( channel==group ) {
                    listener.receive(message)
                }
            }}

        // subscribe redis events
        final name = "redis-future-subscriber-${count.getAndIncrement()}"
        checkRedis(name)
        Thread.startDaemon(name) {
            subscribe(name)
        }
    }

    @Retryable(includes=[JedisConnectionException])
    void checkRedis(name) {
        try(Jedis conn=pool.getResource()) {
            if (!conn.isConnected() || conn.isBroken()) {
                final msg = "Redis connection for '${name}' connected=${conn.isConnected()} and broken=${conn.isBroken()}"
                log.error(msg)
                throw new JedisConnectionException(msg)
            }
        }
    }

    @Retryable(includes=[JedisConnectionException])
    void subscribe(String name) {
        try(Jedis conn=pool.getResource()) {
            log.debug "Redis connection for '${name}' connected=${conn.isConnected()} and broken=${conn.isBroken()}"
            conn.subscribe(subscriber, group)
        }
    }

    @Override
    void publish(String message) {
        try(Jedis conn=pool.getResource() ) {
            conn.publish(group, message)
        }
    }

    @Override
    void close() {
        try {
            subscriber.unsubscribe()
        }
        catch (Throwable e) {
            log.warn "Unexpected error while unsubscribing redis topic '$group' - cause: ${e.message}"
        }
    }
}
