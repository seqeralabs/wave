package io.seqera.wave.service.tokens.impl

import com.fasterxml.jackson.databind.ObjectMapper
import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.TokenConfiguration
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.tokens.ContainerTokenStorage
import jakarta.inject.Singleton


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(property = 'redis.uri')
@Replaces(MemoryTokenStorage)
@Singleton
@CompileStatic
@Slf4j
class RedisTokenStorage implements ContainerTokenStorage{

    ObjectMapper mapper = new ObjectMapper()

    StatefulRedisConnection<String,String> redisConnection

    TokenConfiguration tokenConfiguration

    RedisTokenStorage(
            TokenConfiguration tokenConfiguration,
            StatefulRedisConnection < String, String > redisConnection) {
        this.tokenConfiguration = tokenConfiguration
        this.redisConnection = redisConnection
    }

    @Override
    ContainerRequestData put(String key, ContainerRequestData request) {
        def json = new JsonBuilder(request).toString()
        redisConnection.sync().psetex(key, tokenConfiguration.cache.duration.toMillis(), json)
        return request
    }

    @Override
    ContainerRequestData get(String key) {
        def json = redisConnection.sync().get(key)
        if( !json )
            return null
        def requestData = mapper.readValue(json, ContainerRequestData)
        requestData
    }
}
