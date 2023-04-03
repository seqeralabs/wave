package io.seqera.wave.service.data.queue.impl

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
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

    @Override
    void offer(String key, String message) {
        try (Jedis conn = pool.getResource()) {
            conn.lpush(key, message)
        }
    }

    @Override
    String poll(String key, Duration timeout) {
        try (Jedis conn = pool.getResource()) {
            final result = conn.brpop(timeout.toSeconds() as int , key)
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
    void delete(String key) {
        // clean up the redis queue
        try (Jedis conn = pool.getResource()) {
            conn.del(key)
        }
        catch (Exception e) {
            log.debug "Unexpected exception while deleting queue $key - cause: ${e.message}"
        }
    }

    void init(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.lpush(key)
        }
    }

    boolean exists(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.exists(key)
        }
    }
}
