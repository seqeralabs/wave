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
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiExchange
import org.jetbrains.annotations.Nullable
/**
 * Implement a tiered-cache mechanism using a local caffeine cache as 1st level access
 * and a 2nd-level cache backed on Redis.
 *
 * This allow the use in distributed deployment. Note however strong consistently is not guaranteed.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
abstract class AbstractTieredCache<V extends MoshiExchange> implements TieredCache<String,V> {

    @Canonical
    static class Entry implements MoshiExchange {
        MoshiExchange value
        long expiresAt
    }

    private EncodingStrategy<Entry> encoder

    // FIXME https://github.com/seqeralabs/wave/issues/747
    private AsyncCache<String,V> l1

    private final Duration ttl

    private L2TieredCache<String,String> l2

    private final WeakHashMap<String,Lock> locks = new WeakHashMap<>()

    AbstractTieredCache(L2TieredCache<String,String> l2, MoshiEncodeStrategy encoder, Duration duration, long maxSize) {
        log.info "Cache '${getName()}' config - prefix=${getPrefix()}; ttl=${duration}; max-size: ${maxSize}"
        if( l2==null )
            log.warn "Missing L2 cache for tiered cache '${getName()}'"
        this.l2 = l2
        this.ttl = duration
        this.encoder = encoder
        this.l1 = Caffeine.newBuilder()
                .expireAfterWrite(duration.toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(maxSize)
                .removalListener(removalListener0())
                .buildAsync()
    }

    abstract protected getName()

    abstract protected String getPrefix()

    private RemovalListener removalListener0() {
        new RemovalListener() {
            @Override
            void onRemoval(@Nullable key, @Nullable value, RemovalCause cause) {
                log.trace "Cache '${name}' removing key=$key; value=$value; cause=$cause"
            }
        }
    }

    /**
     * Retrieve the value associated with the specified key
     *
     * @param key
     *      The key of the value to be retrieved
     * @return
     *      The value associated with the specified key, or {@code null} otherwise
     */
    @Override
    V get(String key) {
        getOrCompute(key, null, (v)->true)
    }

    /**
     * Retrieve the value associated with the specified key
     *
     * @param key
     *      The key of the value to be retrieved
     * @param loader
     *      A function invoked to load the value the entry with the specified key is not available
     * @return
     *      The value associated with the specified key, or {@code null} otherwise
     */
    V getOrCompute(String key, Function<String,V> loader) {
        getOrCompute(key, loader, (v)->true)
    }

     /**
     * Retrieve the value associated with the specified key
     *
     * @param key
     *      The key of the value to be retrieved
     * @param loader
     *      The function invoked to load the value the entry with the specified key is not available
     * @param cacheCondition
      *     The function to determine if the loaded value should be cached             
     * @return
      *     The value associated with the specified key, or #function result otherwsie
     */
    V getOrCompute(String key, Function<String,V> loader, Function<V,Boolean> cacheCondition) {
        assert key!=null, "Argument key cannot be null"
        assert cacheCondition!=null, "Argument condition cannot be null"

        log.trace "Cache '${name}' checking key=$key"
        // Try L1 cache first
        V value = l1.synchronous().getIfPresent(key)
        if (value != null) {
            log.trace "Cache '${name}' L1 hit (a) - key=$key => value=$value"
            return value
        }

        final sync = locks.computeIfAbsent(key, (k)-> new ReentrantLock())
        sync.lock()
        try {
            value = l1.synchronous().getIfPresent(key)
            if (value != null) {
                log.trace "Cache '${name}' L1 hit (b) - key=$key => value=$value"
                return value
            }

            // Fallback to L2 cache
            value = l2Get(key)
            if (value != null) {
                log.trace "Cache '${name}' L2 hit - key=$key => value=$value"
                // Rehydrate L1 cache
                l1.synchronous().put(key, value)
                return value
            }

            // still not value found, use loader function to fetch the value
            if( value==null && loader!=null ) {
                log.trace "Cache '${name}' invoking loader - key=$key"
                value = loader.apply(key)
                if( value!=null && cacheCondition.apply(value) ) {
                    l1.synchronous().put(key,value)
                    l2Put(key,value)
                }
            }

            log.trace "Cache '${name}' missing value - key=$key => value=${value}"
            // finally return the value
            return value
        }
        finally {
            sync.unlock()
        }
    }

    @Override
    void put(String key, V value) {
        assert key!=null, "Cache key argument cannot be null"
        assert value!=null, "Cache value argument cannot be null"
        log.trace "Cache '${name}' putting - key=$key; value=${value}"
        l1.synchronous().put(key, value)
        l2Put(key, value)
    }

    protected String key0(String k) { return getPrefix() + ':' + k  }

    protected V l2Get(String key) {
        if( l2 == null )
            return null

        final raw = l2.get(key0(key))
        if( raw == null )
            return null

        final Entry payload = encoder.decode(raw)
        if( System.currentTimeMillis() > payload.expiresAt ) {
            log.trace "Cache '${name}' L2 exipired - key=$key => value=${payload.value}"
            return null
        }
        return (V) payload.value
    }

    protected void l2Put(String key, V value) {
        if( l2 != null ) {
            final raw = encoder.encode(new Entry(value, ttl.toMillis() + System.currentTimeMillis()))
            l2.put(key0(key), raw, ttl)
        }
    }

    void invalidateAll() {
        l1.synchronous().invalidateAll()
    }

}
