package io.seqera.wave.service.data.future.impl

import java.time.Duration
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.future.FutureQueue
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
/**
 * Implements a future queue using Redis list
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'redis')
@Singleton
@CompileStatic
class RedisFutureQueue implements FutureQueue<String>  {

    @Inject
    private JedisPool pool

    @Override
    void offer(String key, String value, Duration expiration) {
        try (Jedis conn = pool.getResource()) {
            final params = new SetParams().ex(expiration.toSeconds())
            conn.set(key, value, params)
        }
    }

    @Override
    String poll(String key) throws TimeoutException {
        try (Jedis conn = pool.getResource()) {
            return conn.get(key)
        }
    }

}
