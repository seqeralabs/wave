package io.seqera.wave.ratelimit.impl

import javax.validation.constraints.NotNull

import com.coveo.spillway.storage.InMemoryStorage
import com.coveo.spillway.storage.LimitUsageStorage
import com.coveo.spillway.storage.RedisStorage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
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
    @Requires(property = 'rate-limit.spillway.impl')
    protected LimitUsageStorage limitUsageStorage(@NotNull RateLimiterConfiguration configuration){
        switch (configuration.spillway.impl){
            case 'memory':
                log.info "Using in memory storage for rate limit"
                return new InMemoryStorage()
            case 'redis':
                log.info "Using redis $configuration.spillway.redisHost host as storage for rate limit"
                def jedisPool = new JedisPool(configuration.spillway.redisHost, configuration.spillway.redisPort)
                return RedisStorage.builder().withJedisPool(jedisPool).build()
            default:
                null
        }
    }

}
