package io.seqera.wave.service.builder.cache

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.Retryable
import io.seqera.wave.configuration.RedisConfig
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'redis.uri')
@Singleton
@CompileStatic
class RedisCacheStore implements CacheStore<String, BuildRequest> {

    private StatefulRedisConnection<String,String> senderConn
    private Duration duration

    RedisCacheStore(StatefulRedisConnection<String,String> senderConn, RedisConfig redisConfig) {
        log.info "Creating Redis build store - duration=$redisConfig.tokenExpired"
        this.senderConn = senderConn
        this.duration = redisConfig.tokenExpired
    }

    @Override
    boolean containsKey(String key) {
        return senderConn.sync().get(key) != null
    }

    @Override
    BuildRequest get(String key) {
        final json = senderConn.sync().get(key)
        if( json==null )
            return null
        return JacksonHelper.fromJson(json, BuildRequest)
    }

    @Override
    void put(String key, BuildRequest value) {
        def json = JacksonHelper.toJson(value)
        // once created the token the user has `Duration` time to pull the layers of the image
        senderConn.sync().psetex(key, duration.toMillis(), json)
    }

    @Override
    BuildRequest await(String key) {
        try {
            BuildRequest ret = checkIfCompleted(key)
            return ret
        }catch( RuntimeException ise ){
            log.info("BuildRequest $key was not present after a wait")
            return null
        }
    }

    @Retryable(attempts = '${redis.store.cache.attempts:300}', delay = '${redis.store.cache.delay:1s}' /*5 mnts by default*/)
    BuildRequest checkIfCompleted(String key){
        final json = senderConn.sync().get(key)
        if( json==null )
            throw new RuntimeException()
        BuildRequest current = JacksonHelper.fromJson(json, BuildRequest)
        if( !current.finished )
            throw new RuntimeException()
        current
    }

}
