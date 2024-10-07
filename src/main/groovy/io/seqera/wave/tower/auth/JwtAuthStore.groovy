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
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements storage for {@link JwtAuth} record
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class JwtAuthStore extends AbstractStateStore<JwtAuth> {

    @Inject
    private JwtTimeStore jwtTimeStore

    JwtAuthStore(StateProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<JwtAuth>() {})
    }

    @Override
    protected String getPrefix() {
        return "tower-jwt-store/v1"
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
        if( !auth )
            return null
        if( !auth.key )
            throw new IllegalArgumentException("Missing JWT auth key attribute")
        final result = this.get(auth.key)
        if( log.isTraceEnabled() ) {
            final msg = result!=null
                    ? "JWT record found in store - $result"
                    : "JWT record not found in store - $auth"
            log.trace(msg)
        }
        if( result && !result.key ) {
            final now = Instant.now()
            final patched = result.withKey(auth.key).withEndpoint(auth.endpoint).withCreatedAt(now).withUpdatedAt(now)
            log.warn "JWT patched legacy record - $patched"
            return patched
        }
        else {
            return result
        }
    }

    void store(JwtAuth auth) {
        final now = Instant.now()
        final entry = auth.withUpdatedAt(now)
        this.put(auth.key, entry)
        log.trace "JWT updating refreshed record - $entry"
    }

    boolean storeIfAbsent(JwtAuth auth) {
        // do not override the stored jwt token, because it may
        // may be newer than the one in the request
        final now = Instant.now()
        final entry = auth
                .withCreatedAt(now)
                .withUpdatedAt(now)
        if( super.putIfAbsent(auth.key, entry) ) {
            log.debug "JWT storing new record - $entry"
            jwtTimeStore.setRefreshTimer(entry.key)
            return true
        }
        else
            return false
    }

}
