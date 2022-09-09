package io.seqera.wave.service.tokens.impl

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.TokenConfiguration
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.tokens.ContainerTokenStorage
import jakarta.inject.Singleton

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Singleton
@CompileStatic
@Slf4j
class LocalTokenStorage implements ContainerTokenStorage{

    private Cache<String, ContainerRequestData> cache

    LocalTokenStorage(TokenConfiguration tokenConfiguration) {
        init(tokenConfiguration.cache.maxSize, tokenConfiguration.cache.duration)
    }

    private void init(long maxSize, Duration maxDuration) {
        log.debug "Creating container tokens cache - maxSize=$maxSize; maxDuration=$maxDuration"
        this.cache = CacheBuilder<String, ContainerRequestData>
                .newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(maxDuration.toSeconds(), TimeUnit.SECONDS)
                .build()
    }

    @Override
    ContainerRequestData put(String key, ContainerRequestData request) {
        cache.put(key, request)
        request
    }

    @Override
    ContainerRequestData get(String key) {
        cache.getIfPresent(key)
    }
}
