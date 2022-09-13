package io.seqera.wave.service.builder.cache

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.builder.BuildRequest
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
@Slf4j
@CompileStatic
class CacheStoreFactory {

    @Requires(missingProperty = 'redis.uri')
    @Singleton
    CacheStore<String, BuildRequest> createLocalStore() {
        log.info "Creating Local build cache"
        new LocalCacheStore<String, BuildRequest>()
    }

    @Requires(property = 'redis.uri')
    @Singleton
    CacheStore<String, BuildRequest> createRedisStore(StatefulRedisConnection<String,String> redis, StatefulRedisPubSubConnection < String, String > redisConnection) {
        log.info "Creating Redis build cache"
        new RedisCacheStore(redis, redisConnection)
    }
}
