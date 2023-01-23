package io.seqera.wave.tower.client

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider

import java.time.Duration

import jakarta.inject.Singleton

/**
 * Implements storage for {@link JwtAuth} record
 *
 */
@Singleton
@CompileStatic
class JwtAuthStore extends AbstractCacheStore<JwtAuth> {

    JwtAuthStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<JwtAuth>() {})
    }

    @Override
    protected String getPrefix() {
        return "tower-jwt-store/v1:"
    }

    @Override
    protected Duration getDuration() {
        return Duration.ofDays(7)
    }

    void putJwtAuth(String endpoint, String providedRefreshToken, String providedAccessToken) {
        if (providedRefreshToken) {
            final tokens = new JwtAuth(providedAccessToken, providedRefreshToken)
            this.put(tokensKey(endpoint, providedAccessToken), tokens)
        }
    }

    JwtAuth putJwtAuth(String endpoint, String originalAccessToken, JwtAuth tokens) {
        this.put(tokensKey(endpoint, originalAccessToken), tokens)
        return tokens
    }

    JwtAuth getJwtAuth(String endpoint, String accessToken) {
        return this.get(tokensKey(endpoint, accessToken))?: new JwtAuth(accessToken)
    }


    private static String tokensKey(String endpoint, String initialRefreshToken) {
        return "${endpoint}:${initialRefreshToken}"
    }
}
