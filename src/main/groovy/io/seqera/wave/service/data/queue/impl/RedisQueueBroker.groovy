package io.seqera.wave.service.data.queue.impl

import java.time.Duration
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.data.queue.MessageBroker
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
/**
 * Implements a message broker using Redis list
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'redis')
@Singleton
@CompileStatic
class RedisQueueBroker implements MessageBroker<String>  {

    @Inject
    private JedisPool pool

    @Value('${wave.pairing.channel.awaitTimeout:100ms}')
    private Duration poolInterval

    @Override
    void offer(String key, String message) {
        try (Jedis conn = pool.getResource()) {
            conn.lpush(key, message)
        }
    }

    @Override
    String poll(String key, Duration timeout) throws TimeoutException {
        final max = timeout.toMillis()
        final begin = System.currentTimeMillis()
        while( true ) {
            final result = poll0(key)
            if( result != null )
                return result
            final delta = System.currentTimeMillis()-begin
            if( delta > max )
                return null
        }
    }

    private String poll0(String key) {
        try (Jedis conn = pool.getResource()) {
            final t0 = poolInterval.toMillis() / 1_000d
            conn.brpop(t0, key)?.getValue()
        }
    }

    @Override
    void delete(String key) {
        // clean up the redis queue
        try (Jedis conn = pool.getResource()) {
            // delete message list
            conn.del(key)
            // delete initialized key
            conn.del(keyInit(key))
        }
        catch (Exception e) {
            log.debug "Unexpected exception while deleting queue $key - cause: ${e.message}"
        }
    }

    @Override
    void init(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.set(keyInit(key), "init")
        }
    }

    @Override
    boolean exists(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.exists(keyInit(key))
        }
    }

    private static String keyInit(String key) {
        return key + "/init"
    }
}
