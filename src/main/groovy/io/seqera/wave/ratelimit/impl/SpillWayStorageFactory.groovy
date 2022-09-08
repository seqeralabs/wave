package io.seqera.wave.ratelimit.impl

import javax.validation.constraints.NotNull

import com.coveo.spillway.storage.InMemoryStorage
import com.coveo.spillway.storage.LimitUsageStorage
import com.coveo.spillway.storage.RedisStorage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.RateLimiterConfiguration
import io.seqera.wave.configuration.RedisConfiguration
import jakarta.inject.Singleton
import redis.clients.jedis.JedisPool

/**
 * A factory for different Spillway implementation
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Factory
@Slf4j
@CompileStatic
class SpillWayStorageFactory {

    @Singleton
    @Requires(missingProperty =  'redis.uri')
    LimitUsageStorage inMemoryStorage(@NotNull RateLimiterConfiguration configuration){
        log.info "Using in memory storage for rate limit"
        new InMemoryStorage()
    }

    @Singleton
    @Requires(property = 'redis.uri')
    LimitUsageStorage redisStorage(@NotNull RateLimiterConfiguration configuration, @NotNull RedisConfiguration redisConfiguration){
        log.info "Using redis $redisConfiguration.uri as storage for rate limit"
        def jedisPool = new JedisPool(redisConfiguration.uri)
        RedisStorage.builder().withJedisPool(jedisPool).build()
    }
}
