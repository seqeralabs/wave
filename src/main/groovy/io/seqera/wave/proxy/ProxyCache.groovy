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

package io.seqera.wave.proxy

import java.time.Duration

import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiExchange
import io.seqera.wave.store.cache.AbstractTieredCache
import io.seqera.wave.store.cache.L2TieredCache
import io.seqera.wave.store.cache.TieredCacheKey
import jakarta.inject.Singleton
/**
 * Implements a tiered cache for proxied http responses
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class ProxyCache extends AbstractTieredCache<TieredCacheKey, DelegateResponse> {

    @Value('${wave.proxy-cache.duration:30m}')
    private Duration duration

    @Value('${wave.proxy-cache.max-size:10000}')
    private int maxSize

    ProxyCache(@Nullable L2TieredCache l2) {
        super(l2, encoder())
    }

    static MoshiEncodeStrategy encoder() {
        // json adapter factory
        final factory = PolymorphicJsonAdapterFactory.of(MoshiExchange.class, "@type")
                .withSubtype(Entry.class, Entry.name)
                .withSubtype(DelegateResponse.class, DelegateResponse.simpleName)
        // the encoding strategy
        return new MoshiEncodeStrategy<AbstractTieredCache.Entry>(factory) {}
    }

    @Override
    String getName() {
        'proxy-cache'
    }

    @Override
    String getPrefix() {
        'proxy-cache/v1'
    }

    @Override
    int getMaxSize() {
        return maxSize
    }

    Duration getDuration() {
        return duration
    }

}
