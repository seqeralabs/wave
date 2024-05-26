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

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements storage for {@link JwtAuth} record
 *
 */
@Singleton
@CompileStatic
class JwtAuthStore extends AbstractCacheStore<JwtAuth> {

    @Inject
    private JwtTimer jwtTimer

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

    /**
     * Try loading a {@link JwtAuth} object with an updated refresh token
     *
     * @param auth The {@link JwtAuth} object as provided in the request
     * @return The "refreshed" {@link JwtAuth} object or {@code null} if cannot be found
     */
    JwtAuth refresh(JwtAuth auth) {
        return auth && auth.refresh
                ? this.get(JwtAuth.key(auth))
                : null
    }

    void store(String key, JwtAuth auth) {
        this.put(key, auth.withUpdatedAt(Instant.now()))
    }

    boolean storeIfAbsent(String key, JwtAuth auth) {
        // do not override the stored jwt token, because it may
        // may be newer than the one in the request
        final now = Instant.now()
        final copy = auth
                .withCreatedAt(now)
                .withUpdatedAt(now)
        if( super.putIfAbsent(key, copy) ) {
            jwtTimer.setRefreshTimer(key)
            return true
        }
        else
            return false
    }

}
