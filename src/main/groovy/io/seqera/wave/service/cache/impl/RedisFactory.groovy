package io.seqera.wave.service.cache.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import redis.clients.jedis.JedisPool

/**
 * Redis connection pool factory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
@Slf4j
@Requires(property = 'redis.uri')
@CompileStatic
class RedisFactory {

    @Singleton
    JedisPool createRedisPool(
            @Value('${redis.uri}') String uri,
            @Value('${redis.pool.minIdle:0}') int minIdle,
            @Value('${redis.pool.maxIdle:10}') int maxIdle,
            @Value('${redis.pool.maxTotal:50}') int maxTotal
    ) {
        log.info "Using redis $uri as storage for rate limit - pool minIdle: ${minIdle}; maxIdle: ${maxIdle}; maxTotal: ${maxTotal}"
        final config = new GenericObjectPoolConfig()
        config.setMinIdle(minIdle)
        config.setMaxIdle(maxIdle)
        config.setMaxTotal(maxTotal)
        return new JedisPool(config, URI.create(uri))
    }

}
