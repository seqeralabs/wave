package io.seqera.wave.ratelimit.impl

import javax.validation.constraints.NotNull

import com.coveo.spillway.storage.InMemoryStorage
import com.coveo.spillway.storage.LimitUsageStorage
import com.coveo.spillway.storage.RedisStorage
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import redis.clients.jedis.JedisPool

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Factory
@Slf4j
class SpillWayStorageFactory {

    @Singleton
    @Requires(property = 'ratelimit.spillway.impl', value = 'memory')
    LimitUsageStorage inMemoryStorage(@NotNull RateLimiterConfiguration configuration){
        log.info "Using in memory storage for rate limit"
        new InMemoryStorage()
    }

    @Singleton
    @Requires(property = 'ratelimit.spillway.impl', value = 'redis')
    LimitUsageStorage redisStorage(@NotNull RateLimiterConfiguration configuration){
        log.info "Using redis $configuration.spillway.redisHost host as storage for rate limit"
        def jedisPool = new JedisPool(configuration.spillway.redisHost, configuration.spillway.redisPort)
        RedisStorage.builder().withJedisPool(jedisPool).build()
    }
}
