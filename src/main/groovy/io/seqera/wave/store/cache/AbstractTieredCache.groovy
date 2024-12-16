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

package io.seqera.wave.store.cache

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Function

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
/**
 * Abstract implementation for tiered-cache
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class AbstractTieredCache<V> implements TieredCache<String,V> {

    @Canonical
    static class Payload {
        Object value
        long expiresAt
    }

    private EncodingStrategy<Payload> encoder

    private AsyncCache<String,V> l1

    private final Duration ttl

    private L2TieredCache<String,String> l2

    AbstractTieredCache(L2TieredCache<String,String> l2, Duration ttl, long maxSize) {
        this.l2 = l2
        this.ttl = ttl
        this.encoder = new MoshiEncodeStrategy<Payload>() {}
        this.l1 = Caffeine.newBuilder()
                .expireAfterWrite(ttl.toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(maxSize)
                .buildAsync()
    }

    abstract protected String getPrefix()

    @Override
    V get(String key) {
        // Try local cache first
        V value = l1.synchronous().getIfPresent(key)
        if (value != null) {
            return value;
        }

        // Fallback to L2 cache
        value = l2Get(key)
        if (value != null) {
            // Rehydrate Caffeine cache
            l1.synchronous().put(key, value)
        }

        return value
    }

    V getOrCompute(String key, Function<String,V> loader) {
        def result = get(key)
        if( result!=null ) {
            return result
        }

        result = loader.apply(key)
        if( result!=null ) {
            l1.synchronous().put(key,result)
            l2Put(key,result)
        }

        return result
    }

    @Override
    void put(String key, V value) {
        // Store in Caffeine
        l1.synchronous().put(key, value)
        // Store in Redis with a TTL
        l2Put(key, value)
    }

    protected String key0(String k) { return getPrefix() + ':' + k  }

    protected V l2Get(String key) {
        if( l2 == null )
            return null

        final raw = l2.get(key0(key))
        if( raw == null )
            return null

        final Payload payload = encoder.decode(raw)
        return System.currentTimeMillis() <= payload.expiresAt
                ? (V) payload.value
                : null
    }

    protected void l2Put(String key, V value) {
        if( l2 != null ) {
            final raw = encoder.encode(new Payload(value, ttl.toMillis() + System.currentTimeMillis()))
            l2.put(key0(key), raw, ttl)
        }
    }

    void invalidateAll() {
        l1.synchronous().invalidateAll()
    }
}
