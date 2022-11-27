package io.seqera.wave.service.token

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.TokenConfig
import io.seqera.wave.service.ContainerRequestData
import jakarta.inject.Singleton

/**
 * Implements container request token store based on a local cache
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Deprecated
@Singleton
@CompileStatic
@Slf4j
class LocalTokenStore implements ContainerTokenStore {

    private Cache<String, ContainerRequestData> cache

    LocalTokenStore(TokenConfig tokenConfig) {
        init(tokenConfig.cache.maxSize, tokenConfig.cache.duration)
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
        return request
    }

    @Override
    ContainerRequestData get(String key) {
        cache.getIfPresent(key)
    }
}
