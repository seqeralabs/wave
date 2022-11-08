package io.seqera.wave.service.token

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.TokenConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton


/**
 * Implements container request token store based on a cache
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Singleton
@CompileStatic
@Slf4j
class ContainerTokenCache extends AbstractCacheStore<ContainerRequestData> implements ContainerTokenStore{

    TokenConfig tokenConfig

    ContainerTokenCache(CacheProvider<String, String> delegate,
                        TokenConfig tokenConfig) {
        super(delegate, new MoshiEncodeStrategy<ContainerRequestData>(){})
        this.tokenConfig = tokenConfig
        log.debug "Creating container tokens cache - maxSize=$tokenConfig.cache.maxSize; maxDuration=$tokenConfig.cache.duration"
    }

    @Override
    protected String getPrefix() {
        return "wave-tokens/v1:"
    }

    @Override
    protected Duration getTimeout() {
        return tokenConfig.cache.duration
    }

    @Override
    ContainerRequestData putRequest(String key, ContainerRequestData request) {
        putIfAbsent(key, request, tokenConfig.cache.duration)
        request
    }

    @Override
    ContainerRequestData getRequest(String key) {
        get(key)
    }
}
