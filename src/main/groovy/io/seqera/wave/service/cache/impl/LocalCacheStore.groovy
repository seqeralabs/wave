package io.seqera.wave.service.cache.impl

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

/**
 * Simple cache store implementation for development purpose
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class LocalCacheStore implements CacheProvider<String,String> {

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

    @Override
    void put(String key, String value) {
        store.put(key, new Entry<>(value))
    }

    void put(String key, String value, Duration ttl) {
        store.put(key, new Entry<>(value,ttl))
    }

    @Override
    boolean putIfAbsent(String key, String value) {
        final entry = store.get(key)
        if( entry?.isExpired() )
            store.remove(key)
        return store.putIfAbsent(key, new Entry<>(value))==null
    }

    @Override
    void remove(String key) {
        store.remove(key)
    }
}
