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
    void offer(String key, String value) {
        try (Jedis conn = pool.getResource()) {
            conn.lpush(key, value)
        }
    }

    @Override
    String poll(String key, Duration timeout) throws TimeoutException {
        final t0 = timeout.toMillis() / 1_000d
        try (Jedis conn = pool.getResource()) {
            final result = conn.brpop(t0, key)?.getValue()
            if( result != null )
                return result
            throw new TimeoutException("Unable to retrieve a value for key: $key")
        }
    }

}
