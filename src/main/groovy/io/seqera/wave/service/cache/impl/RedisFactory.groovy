package io.seqera.wave.service.cache.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
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
    JedisPool createRedisPool(@Value('${redis.uri}') String uri) {
        log.info "Using redis $uri as storage for rate limit"
        return new JedisPool(uri)
    }

}
