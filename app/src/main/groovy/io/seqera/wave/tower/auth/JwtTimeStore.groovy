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

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.store.range.AbstractRangeStore
import io.seqera.wave.store.range.impl.RangeProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements linear store for JWT record keys to track
 * tokens that needs to be refreshed periodically
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class JwtTimeStore extends AbstractRangeStore {

    @Inject
    private JwtConfig jwtConfig

    JwtTimeStore(RangeProvider provider) {
        super(provider)
    }

    @Override
    final protected String getKey() {
        return 'tower-jwt-timestore/v1'
    }

    void setRefreshTimer(String key) {
        this.add(key, expireSecs0())
    }

    private long expireSecs0() {
        Instant.now().epochSecond + jwtConfig.refreshInterval.toSeconds()
    }

}
