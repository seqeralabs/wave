package io.seqera.wave.service.cache

import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Inject

/**
 * Implements a generic cache store
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class AbstractCacheStore<V> implements CacheStore<String,V> {

    @Inject
    private EncodingStrategy<V> encodingStrategy

    @Inject
    private CacheProvider<String,String> delegate

    protected abstract String getPrefix()

    protected String key0(String k) { return getPrefix() + k  }

    protected V deserialize(String encoded) {
        return encodingStrategy.decode(encoded)
    }

    String serialize(V value) {
        return encodingStrategy.encode(value)
    }

    @Override
    V get(String key) {
        final result = delegate.get(key0(key))
        return result ? deserialize(result) : null
    }

    @Override
    void put(String key, V value) {
        delegate.put(key0(key), serialize(value))
    }

    @Override
    boolean putIfAbsent(String key, V value) {
        delegate.putIfAbsent(key0(key), serialize(value))
    }

    @Override
    void remove(String key) {
        delegate.remove(key0(key))
    }
}
