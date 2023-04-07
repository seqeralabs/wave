package io.seqera.wave.service.data.queue.impl

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.queue.MessageBroker
import io.seqera.wave.service.pairing.socket.MessageSender
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

    private ConcurrentHashMap<String, MessageSender<String>> clients = new ConcurrentHashMap<>()

    @Override
    void offer(String target, String message) {
        try (Jedis conn = pool.getResource()) {
            conn.lpush(target, message)
        }
    }

    @Override
    String poll(String target) {
        try (Jedis conn = pool.getResource()) {
            return conn.rpop(target)
        }
    }

    @Override
    boolean matches(String target) {
        try (Jedis conn = pool.getResource()) {
            return conn.keys(target + '*')?.size()>0
        }
    }

    @Override
    void mark(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.set(key, 'true')
        }
    }

    @Override
    void unmark(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.del(key)
        }
    }

}
