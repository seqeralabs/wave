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
        try {
            return poll1(key, timeout)
        }
        finally {
            delete0(key)
        }
    }

    private String poll0(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.brpop(0.2d, key)?.getValue()
        }
    }

    private String poll1(String key, Duration timeout) {
        final max = timeout.toMillis()
        final begin = System.currentTimeMillis()
        while( true ) {
            final result = poll0(key)
            if( result != null )
                return result
            if( System.currentTimeMillis()-begin > max )
                throw new TimeoutException("Unable to retrieve a value for key: $key")
        }
    }

    private void delete0(String key) {
        try (Jedis conn = pool.getResource()) {
            conn.del(key)
        }
    }

}
