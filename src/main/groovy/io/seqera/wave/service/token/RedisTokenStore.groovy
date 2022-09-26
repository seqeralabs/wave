package io.seqera.wave.service.token


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.TokenConfig
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton 
/**
 * Implements container request token store based on a Redis cache
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(property = 'redis.uri')
@Replaces(LocalTokenStore)
@Singleton
@CompileStatic
@Slf4j
class RedisTokenStore implements ContainerTokenStore {

    StatefulRedisConnection<String,String> redisConnection

    TokenConfig tokenConfiguration

    RedisTokenStore(
            TokenConfig config,
            StatefulRedisConnection<String,String> redisConnection) {
        this.tokenConfiguration = config
        this.redisConnection = redisConnection
        log.info "Redis tokens store - duration=$config.cache.duration; maxSize=$config.cache.maxSize"
    }

    private String key(String name) { "wave-tokens/v1:$name" }

    @Override
    ContainerRequestData put(String name, ContainerRequestData request) {
        final json = JacksonHelper.toJson(request)
        // once created the token the user has `Duration` time to pull the layers of the image
        redisConnection.sync().psetex(key(name), tokenConfiguration.cache.duration.toMillis(), json)
        return request
    }

    @Override
    ContainerRequestData get(String name) {
        def json = redisConnection.sync().get(key(name))
        if( !json )
            return null
        return JacksonHelper.fromJson(json, ContainerRequestData)
    }
}
