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
import java.time.Instant
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Function

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.ToString
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
    @ToString(includePackage = false, includeNames = true)
    static class Entry implements MoshiExchange {
        MoshiExchange value
        long expiresAt
    }

    private EncodingStrategy<Entry> encoder

    // FIXME https://github.com/seqeralabs/wave/issues/747
    private volatile AsyncCache<String,Entry> _l1

    private L2TieredCache<String,String> l2

    // FIXME https://github.com/seqeralabs/wave/issues/747
    private AsyncLoadingCache<String,Lock> locks = Caffeine.newBuilder()
            .maximumSize(5_000)
            .weakKeys()
            .buildAsync(loader())

    CacheLoader<String,Lock> loader() {
        (String key) -> new ReentrantLock()
    }

    AbstractTieredCache(L2TieredCache<String,String> l2, MoshiEncodeStrategy encoder) {
        if( l2==null )
            log.warn "Missing L2 cache for tiered cache '${getName()}'"
        this.l2 = l2
        this.encoder = encoder
    }

    private Cache<String,Entry> getL1() {
        if( _l1!=null )
            return _l1.synchronous()

        final sync = locks.get('sync-l1').get()
        sync.lock()
        try {
            if( _l1!=null )
                return _l1.synchronous()
            
            log.info "Cache '${getName()}' config - prefix=${getPrefix()}; max-size: ${maxSize}"
            _l1 = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .removalListener(removalListener0())
                    .buildAsync()
            return _l1.synchronous()
        }
        finally {
            sync.unlock()
        }
    }

    abstract protected int getMaxSize()

    abstract protected getName()

    abstract protected String getPrefix()

    /**
     * The cache probabilistic revalidation internal.
     *
     * See  https://blog.cloudflare.com/sometimes-i-cache/
     *
     * @return
     *      The cache cache revalidation internal as a {@link Duration} value.
     *      When {@link Duration#ZERO} probabilistic revalidation is disabled.
     */
    protected Duration getCacheRevalidationInterval() {
        return Duration.ZERO
    }

    /**
     * The cache probabilistic revalidation steepness value.
     *
     * By default is implemented as 1 / {@link #getCacheRevalidationInterval()} (as millis).
     * Subclasses can override this method to provide a different value.
     *
     * See https://blog.cloudflare.com/sometimes-i-cache/
     *
     * @return Returns the revalidation steepness value.
     */
    @Memoized
    protected double getRevalidationSteepness() {
        return 1 / getCacheRevalidationInterval().toMillis()
    }

    private RemovalListener removalListener0() {
        new RemovalListener() {
            @Override
            void onRemoval(@Nullable key, @Nullable value, RemovalCause cause) {
                if( log.isTraceEnabled() ) {
                    log.trace "Cache '${name}' removing key=$key; value=$value; cause=$cause"
                }
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
        getOrCompute0(key, null)
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
    V getOrCompute(String key, Function<String,V> loader, Duration ttl) {
        if( loader==null ) {
            return getOrCompute0(key, null)
        }
        return getOrCompute0(key, (String k)-> {
            V v = loader.apply(key)
            return v != null ? new Tuple2<>(v, ttl) : null
        })
    }

     /**
     * Retrieve the value associated with the specified key
     *
     * @param key
     *      The key of the value to be retrieved
     * @param loader
     *      The function invoked to load the value the entry with the specified key is not available
     * @return
      *     The value associated with the specified key, or #function result otherwise
     */
    V getOrCompute(String key, Function<String, Tuple2<V,Duration>> loader) {
        return getOrCompute0(key, loader)
    }

    private V getOrCompute0(String key, Function<String, Tuple2<V,Duration>> loader) {
        assert key!=null, "Argument key cannot be null"
        if( log.isTraceEnabled() )
            log.trace "Cache '${name}' checking key=$key"
        final ts = Instant.now()
        // Try L1 cache first
        Entry entry = l1Get(key)
        Boolean needsRevalidation = entry ? shouldRevalidate(entry.expiresAt, ts) : null
        if( entry && !needsRevalidation ) {
            if( log.isTraceEnabled() )
                log.trace "Cache '${name}' L1 hit (a) - key=$key => entry=$entry"
            return (V) entry.value
        }

        final sync = locks.get(key).get()
        sync.lock()
        try {
            // check again L1 cache once in the sync block
            if( !entry ) {
                entry = l1Get(key)
                needsRevalidation = entry ? shouldRevalidate(entry.expiresAt, ts) : null
            }
            if( entry && !needsRevalidation ) {
                if( log.isTraceEnabled() )
                    log.trace "Cache '${name}' L1 hit (b) - key=$key => entry=$entry"
                return (V)entry.value
            }

            // Fallback to L2 cache
            if( !entry ) {
                entry = l2Get(key)
                needsRevalidation = entry ? shouldRevalidate(entry.expiresAt, ts) : null
            }
            if( entry && !needsRevalidation ) {
                if( log.isTraceEnabled() )
                    log.trace "Cache '${name}' L2 hit (c) - key=$key => entry=$entry"
                // Rehydrate L1 cache
                l1.put(key, entry)
                return (V) entry.value
            }

            // still not entry found or cache revalidation needed
            // use the loader function to fetch the value
            V value = null
            if( loader!=null ) {
                if( entry && needsRevalidation )
                    log.debug "Cache '${name}' invoking loader - entry=$entry needs refresh"
                else if( log.isTraceEnabled() )
                    log.trace "Cache '${name}' invoking loader - key=$key"
                final ret = loader.apply(key)
                value = ret?.v1
                Duration ttl = ret?.v2
                if( value!=null && ttl!=null ) {
                    final exp = Instant.now().plus(ttl).toEpochMilli()
                    final newEntry = new Entry(value, exp)
                    l1Put(key, newEntry)
                    l2Put(key, newEntry, ttl)
                }
            }

            if( log.isTraceEnabled() )
                log.trace "Cache '${name}' missing value - key=$key => value=${value}"
            // finally return the value
            return value
        }
        finally {
            sync.unlock()
        }
    }

    @Override
    void put(String key, V value, Duration ttl) {
        assert key!=null, "Cache key argument cannot be null"
        assert value!=null, "Cache value argument cannot be null"
        if( log.isTraceEnabled() )
            log.trace "Cache '${name}' putting - key=$key; value=${value}"
        final exp = System.currentTimeMillis() + ttl.toMillis()
        final entry = new Entry(value, exp)
        l1Put(key, entry)
        l2Put(key, entry, ttl)
    }

    protected String key0(String k) { return getPrefix() + ':' + k  }

    protected Entry l1Get(String key) {
        return l1.getIfPresent(key)
    }

    protected void l1Put(String key, Entry entry) {
        l1.put(key, entry)
    }

    protected Entry l2Get(String key) {
        if( l2 == null )
            return null

        final raw = l2.get(key0(key))
        if( raw == null )
            return null

        return encoder.decode(raw)
    }


    protected void l2Put(String key, Entry entry, Duration ttl) {
        if( l2 != null ) {
            final raw = encoder.encode(entry)
            l2.put(key0(key), raw, ttl)
        }
    }

    void invalidateAll() {
        l1.invalidateAll()
    }

    protected boolean shouldRevalidate(long expiration, Instant time=Instant.now()) {
        // when 'remainingCacheTime' is less than or equals to zero, it means
        // the current time is beyond the expiration time, therefore a cache validation is needed
        final remainingCacheTime = expiration - time.toEpochMilli()
        if (remainingCacheTime <= 0) {
            return true
        }

        // otherwise, when remaining is greater than the cache revalidation interval
        // no revalidation is needed
        final cacheRevalidationMills = cacheRevalidationInterval.toMillis()
        if( cacheRevalidationMills < remainingCacheTime ) {
            return false
        }

        // finally the remaining time is shorter the validation interval
        // i.e. it's approaching the cache expiration, in this cache the needed
        // for cache revalidation is determined in a probabilistic manner
        // see https://blog.cloudflare.com/sometimes-i-cache/
        return randomRevalidate(cacheRevalidationMills-remainingCacheTime)
    }

    protected boolean randomRevalidate(long remainingTime) {
        return Math.random() < Math.exp(-revalidationSteepness * remainingTime)
    }

}
