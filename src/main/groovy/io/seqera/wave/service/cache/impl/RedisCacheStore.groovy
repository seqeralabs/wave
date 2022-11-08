package io.seqera.wave.service.cache.impl

import java.time.Duration

import groovy.transform.CompileStatic
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Redis based implementation for a {@link CacheProvider}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(env = 'redis')
@Replaces(LocalCacheStore)
@CompileStatic
class RedisCacheStore implements CacheProvider<String,String> {

    @Inject
    private StatefulRedisConnection<String,String> redisConn

    @Override
    String get(String key) {
        return redisConn.sync().get(key)
    }

    void put(String key, String value, Duration ttl) {
        redisConn.sync().psetex(key, ttl.toMillis(), value)
    }

    @Override
    boolean putIfAbsent(String key, String value, Duration ttl) {
        final SetArgs args = SetArgs.Builder.ex(ttl).nx()
        final result = redisConn.sync().set(key, value, args)
        return result == 'OK'
    }

    @Override
    void remove(String key) {
        redisConn.sync().del(key)
    }

}
