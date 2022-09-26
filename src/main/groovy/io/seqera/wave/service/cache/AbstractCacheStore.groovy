package io.seqera.wave.service.cache

import io.seqera.wave.service.cache.CacheStore

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class AbstractCacheStore<V> implements CacheStore<String,V> {

    private CacheStore<String,String> delegate

    protected abstract String getPrefix()

    protected String key0(String k) { return getPrefix() + k  }

    protected V deserialize(String encoded) {

    }

    protected String serialize(V value) {

    }

    V get(String key) {
        final result = delegate.get(key0(key))
        return result ? deserialize(result) : null
    }

    void put(String key, V value) {
        delegate.put(key0(key), serialize(value))
    }

    boolean putIfAbsent(String key, V value) {
        delegate.putIfAbsent(key0(key), serialize(value))
    }

    void remove(String key) {
        delegate.remove(key0(key))
    }
}
