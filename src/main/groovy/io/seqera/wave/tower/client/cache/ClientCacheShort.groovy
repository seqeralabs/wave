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

package io.seqera.wave.tower.client.cache

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.store.cache.AbstractTieredCache
import io.seqera.wave.store.cache.L2TieredCache
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class ClientCacheShort extends AbstractTieredCache {
    ClientCacheShort(@Nullable L2TieredCache l2,
                     @Value('${wave.pairing.client.short.ttl:60s}') Duration ttl,
                     @Value('${wave.pairing.client.short.max-size:10000}') int maxSize)
    {
        super(l2, ttl, maxSize)
    }

    @Override
    protected String getPrefix() {
        return 'pairing-cache-short/v1'
    }
}