package io.seqera.wave.service.data.future.impl

import java.time.Duration
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
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

    @Value('${wave.pairing.channel.awaitTimeout:100ms}')
    private Duration poolInterval

    @Override
    void offer(String key, String value, Duration timeout) {
        try (Jedis conn = pool.getResource()) {
            final params = new SetParams().ex(timeout.toSeconds())
            conn.set(key, value, params)
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
                throw new TimeoutException("Unable to retrieve a value for key: $key (delta: ${delta}ms)")
            else
                sleep poolInterval.toMillis()
        }
    }

    private String poll0(String key) {
        try (Jedis conn = pool.getResource()) {
            return conn.get(key)
        }
    }

}
