package io.seqera.service

import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import groovy.transform.CompileStatic
import io.seqera.util.LongRndKey
import jakarta.inject.Singleton
import com.google.common.cache.CacheBuilder

/**
 * Service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Singleton
class ContainerTokenServiceImpl implements ContainerTokenService {

    private Cache<String, ContainerRequestData> cache = CacheBuilder<String, ContainerRequestData>
            .newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build()

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
