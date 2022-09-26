package io.seqera.wave.service.cache.impl

import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import io.seqera.wave.service.cache.CacheStore

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class LocalCacheStore implements CacheStore<String,String> {

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
