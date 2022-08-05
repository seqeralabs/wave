package io.seqera.wave.service

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Singleton
/**
 * Service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
class ContainerTokenServiceImpl implements ContainerTokenService {

    @Value('${wave.tokens.cache.maxDuration:1h}')
    private Duration maxDuration

    @Value('${wave.tokens.cache.maxSize:10000}')
    private int maxSize

    private Cache<String, ContainerRequestData> cache

    private void init() {
        log.debug "Creating container tokens cache - maxSize=$maxSize; maxDuration=$maxDuration"
        this.cache = CacheBuilder<String, ContainerRequestData>
                .newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(maxDuration.toSeconds(), TimeUnit.SECONDS)
                .build()
    }

    @Override
    String getToken(ContainerRequestData request) {
        final token = LongRndKey.rndHex()
        cache.put(token, request)
        return token
    }

    @Override
    ContainerRequestData getRequest(String token) {
        return cache.getIfPresent(token)
    }
}
