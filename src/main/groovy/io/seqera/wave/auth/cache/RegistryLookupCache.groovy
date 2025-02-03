/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.auth.cache

import java.time.Duration

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiExchange
import io.seqera.wave.store.cache.AbstractTieredCache
import io.seqera.wave.store.cache.L2TieredCache
import io.seqera.wave.store.cache.TieredCacheKey
import jakarta.inject.Singleton
/**
 * Implement a tiered cache for Registry lookup
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class RegistryLookupCache extends AbstractTieredCache<String, RegistryAuth> {

    @Value('${wave.registry.cache.duration:1h}')
    private Duration duration

    @Value('${wave.registry.cache.max-size:10000}')
    private int maxSize

    RegistryLookupCache(@Nullable L2TieredCache l2) {
        super(l2, encoder())
    }

    @Override
    int getMaxSize() {
        return maxSize
    }

    @Override
    protected getName() {
        return 'registry-cache'
    }

    @Override
    protected String getPrefix() {
        return 'registry-cache/v1'
    }

    static MoshiEncodeStrategy encoder() {
        new MoshiEncodeStrategy<AbstractTieredCache.Entry>(factory()) {}
    }

    static JsonAdapter.Factory factory() {
        PolymorphicJsonAdapterFactory.of(MoshiExchange.class, "@type")
                .withSubtype(AbstractTieredCache.Entry.class, AbstractTieredCache.Entry.name)
                .withSubtype(RegistryAuth.class, RegistryAuth.name)
    }

}
