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
 * @param <K>
 *      The type of keys maintained by this cache. Note it must be either a
 *      subtype of {@link CharSequence} or an implementation of {@link TieredCacheKey} interface.
 * @param <V>
 *      The type of values maintained by this cache, which must extend {@link MoshiExchange}.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
abstract class AbstractTieredCache<K, V extends MoshiExchange> implements TieredCache<K,V> {

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

    abstract int getMaxSize()

    abstract protected getName()

    abstract protected String getPrefix()

    private RemovalListener removalListener0() {
        new RemovalListener() {
            @Override
            void onRemoval(@Nullable key, @Nullable value, RemovalCause cause) {
                if( log.isTraceEnabled( )) {
                    log.trace "Cache '${name}' removing key=$key; value=$value; cause=$cause"
                }
            }
        }
    }

    protected String k0(K key) {
        if( key instanceof CharSequence )
            return key.toString()
        if( key instanceof TieredCacheKey )
            return key.stableHash()
        if( key==null )
            throw new IllegalArgumentException("Tiered cache key cannot be null")
        else
            throw new IllegalArgumentException("Tiered cache key type - offending value: ${key}; type: ${key.getClass()}")
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
    V get(K key) {
        getOrCompute0(k0(key), null)
    }

    /**
     * Retrieve the value associated with the specified key
     *
     * @param key
     *      The key of the value to be retrieved
     * @param loader
     *      A function invoked to load the value the entry with the specified key is not available
     * @param ttl
     *      time to live for the entry
     * @return
     *      The value associated with the specified key, or {@code null} otherwise
     */
    V getOrCompute(K key, Function<String,V> loader, Duration ttl) {
        if( loader==null ) {
            return getOrCompute0(k0(key), null)
        }
        return getOrCompute0(k0(key), (String k)-> {
            V v = loader.apply(k)
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
    V getOrCompute(K key, Function<String, Tuple2<V,Duration>> loader) {
        return getOrCompute0(k0(key), loader)
    }

    private V getOrCompute0(String key, Function<String, Tuple2<V,Duration>> loader) {
        assert key!=null, "Argument key cannot be null"

        if( log.isTraceEnabled() )
            log.trace "Cache '${name}' checking key=$key"
        // Try L1 cache first
        V value = l1Get(key)
        if (value != null) {
            if( log.isTraceEnabled() )
                log.trace "Cache '${name}' L1 hit (a) - key=$key => value=$value"
            return value
        }

        final sync = locks.get(key).get()
        sync.lock()
        try {
            value = l1Get(key)
            if (value != null) {
                if( log.isTraceEnabled() )
                    log.trace "Cache '${name}' L1 hit (b) - key=$key => value=$value"
                return value
            }

            // Fallback to L2 cache
            final entry = l2GetEntry(key)
            if (entry != null) {
                if( log.isTraceEnabled() )
                    log.trace "Cache '${name}' L2 hit - key=$key => entry=$entry"
                // Rehydrate L1 cache
                l1.put(key, entry)
                return (V) entry.value
            }

            // still not value found, use loader function to fetch the value
            if( value==null && loader!=null ) {
                if( log.isTraceEnabled() )
                    log.trace "Cache '${name}' invoking loader - key=$key"
                final ret = loader.apply(key)
                value = ret?.v1
                Duration ttl = ret?.v2
                if( value!=null && ttl!=null ) {
                    final exp = Instant.now().plus(ttl).toEpochMilli()
                    final newEntry = new Entry(value,exp)
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
    void put(K key, V value, Duration ttl) {
        assert key!=null, "Cache key argument cannot be null"
        assert value!=null, "Cache value argument cannot be null"
        if( log.isTraceEnabled() )
            log.trace "Cache '${name}' putting - key=$key; value=${value}"
        final exp = System.currentTimeMillis() + ttl.toMillis()
        final entry = new Entry(value, exp)
        l1Put(k0(key), entry)
        l2Put(k0(key), entry, ttl)
    }

    protected String key0(String k) { return getPrefix() + ':' + k  }

    protected V l1Get(String key) {
        return (V) l1GetEntry(key)?.value
    }

    protected Entry l1GetEntry(String key) {
        final entry = l1.getIfPresent(key)
        if( entry == null )
            return null

        if( System.currentTimeMillis() > entry.expiresAt ) {
            if( log.isTraceEnabled() )
                log.trace "Cache '${name}' L1 expired - key=$key => entry=$entry"
            return null
        }
        return entry
    }

    protected void l1Put(String key, Entry entry) {
        l1.put(key, entry)
    }

    protected Entry l2GetEntry(String key) {
        if( l2 == null )
            return null

        final raw = l2.get(key0(key))
        if( raw == null )
            return null

        final Entry entry = encoder.decode(raw)
        if( System.currentTimeMillis() > entry.expiresAt ) {
            if( log.isTraceEnabled() )
                log.trace "Cache '${name}' L2 expired - key=$key => value=${entry}"
            return null
        }
        return entry
    }

    protected V l2Get(String key) {
       return (V) l2GetEntry(key)?.value
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

}
