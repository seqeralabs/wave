package io.seqera.wave.service.cache.impl

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Simple cache store implementation for development purpose
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalCacheProvider implements CacheProvider<String,String> {

    private static class Entry<V> {
        final V value
        final Duration ttl
        final Instant ts

        Entry(V value, Duration ttl=null) {
            this.value = value
            this.ttl = ttl
            this.ts = Instant.now()
        }

        boolean isExpired() {
            return ttl!=null ? ts.plus(ttl) <= Instant.now() : false
        }
    }

    private Map<String,Entry<String>> store = new ConcurrentHashMap<>()

    @Override
    String get(String key) {
        final entry = store.get(key)
        if( !entry ) {
            return null
        }
        if( entry.isExpired() ) {
            store.remove(key)
            return null
        }
        return entry.value
    }

    void put(String key, String value, Duration ttl) {
        store.put(key, new Entry<>(value,ttl))
    }

    @Override
    boolean putIfAbsent(String key, String value, Duration ttl) {
        return putIfAbsent0(key, value, ttl) == null
    }

    @Override
    String putIfAbsentAndGetCurrent(String key, String value, Duration ttl) {
        final ret = putIfAbsent0(key, value, ttl)
        return ret!=null ? ret : value
    }

    private String putIfAbsent0(String key, String value, Duration ttl) {
        final entry = store.get(key)
        if( entry?.isExpired() )
            store.remove(key)
        return store.putIfAbsent(key, new Entry<>(value,ttl))?.value
    }

    @Override
    void remove(String key) {
        store.remove(key)
    }

    @Override
    void clear() {
        store.clear()
    }

    // =============== bi-cache store implementation ===============

    private Map<Integer,Set<String>> index = new HashMap<>()

    @Override
    void biPut(String key, String value, Duration ttl) {
        synchronized (this) {
            this.put(key, value, ttl)
            final id = value.hashCode()
            def set = index.get(id)
            if( set==null ) {
                set=new HashSet<String>()
                index.put(id, set)
            }
            set.add(key)
        }
    }

    @Override
    void biRemove(String key) {
        synchronized (this) {
            final entry = store.remove(key)
            if( !entry )
                return 
            final id = entry.value.hashCode()
            final set = index.get(id)
            if( set ) {
                set.remove(key)
            }
        }
    }

    @Override
    Set<String> biKeysFor(String value) {
        final id = value.hashCode()
        return index.get(id) ?: Set.<String>of()
    }

    String biKeyFind(String value, boolean shuffled) {
        final id = value.hashCode()
        final list = biKeysFor(value).toList()
        final keys = shuffled ? list.shuffled() : list
        final itr = keys.iterator()
        while( itr.hasNext() ) {
            final result = itr.next()
            if( get(result)!=null )
                return result
            index.get(id)?.remove(result)
        }
        return null
    }
}
