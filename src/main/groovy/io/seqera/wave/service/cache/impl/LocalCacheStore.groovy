package io.seqera.wave.service.cache.impl

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

    private Map<String,String> store = new ConcurrentHashMap<>()

    @Override
    String get(String key) {
        return store.get(key)
    }

    @Override
    void put(String key, String value) {
        store.put(key,value)
    }

    @Override
    boolean putIfAbsent(String key, String value) {
        return store.putIfAbsent(key,value) == null
    }

    @Override
    void remove(String key) {
        store.remove(key)
    }
}
