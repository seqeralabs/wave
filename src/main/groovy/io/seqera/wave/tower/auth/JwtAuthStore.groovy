/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.tower.auth

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider

import java.time.Duration

import io.seqera.wave.util.DigestFunctions
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

    /**
     * Token should survive in the cache for all the lifespan of tower pipeline execution
     * 
     * @return The duration the token should service in the cache
     */
    @Override
    protected Duration getDuration() {
        return Duration.ofDays(30)
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
        return DigestFunctions.md5("${endpoint}:${initialRefreshToken}")
    }
}
