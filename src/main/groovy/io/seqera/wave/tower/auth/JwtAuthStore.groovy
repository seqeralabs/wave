/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
