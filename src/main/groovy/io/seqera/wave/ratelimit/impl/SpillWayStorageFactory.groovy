package io.seqera.wave.ratelimit.impl

import javax.validation.constraints.NotNull

import com.coveo.spillway.storage.InMemoryStorage
import com.coveo.spillway.storage.LimitUsageStorage
import com.coveo.spillway.storage.RedisStorage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.configuration.RedisConfig
import jakarta.inject.Singleton
import redis.clients.jedis.JedisPool

/**
 * A factory for different Spillway implementation
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env = 'rate-limit')
@Factory
@Slf4j
@CompileStatic
class SpillWayStorageFactory {

    @Singleton
    @Requires(missingProperty =  'redis.uri')
    LimitUsageStorage inMemoryStorage(@NotNull RateLimiterConfig config){
        log.info "Using in memory storage for rate limit"
        return new InMemoryStorage()
    }

    @Singleton
    @Requires(property = 'redis.uri')
    LimitUsageStorage redisStorage(@NotNull RateLimiterConfig config, @NotNull RedisConfig redisConfig){
        log.info "Using redis $redisConfig.uri as storage for rate limit"
        def jedisPool = new JedisPool(redisConfig.uri)
        return RedisStorage.builder().withJedisPool(jedisPool).build()
    }
}
