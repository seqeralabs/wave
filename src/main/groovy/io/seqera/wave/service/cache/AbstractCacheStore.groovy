package io.seqera.wave.service.cache

import java.time.Duration

import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.service.cache.impl.CacheProvider

/**
 * Implements a generic cache store
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class AbstractCacheStore<V> implements CacheStore<String,V> {

    private EncodingStrategy<V> encodingStrategy

    private CacheProvider<String,String> delegate

    AbstractCacheStore(CacheProvider<String,String> provider, EncodingStrategy<V> encodingStrategy) {
        this.delegate = provider
        this.encodingStrategy = encodingStrategy
    }

    protected abstract String getPrefix()

    protected abstract Duration getDuration()

    protected String key0(String k) { return getPrefix() + k  }

    protected V deserialize(String encoded) {
        return encodingStrategy.decode(encoded)
    }

    String serialize(V value) {
        return encodingStrategy.encode(value)
    }

    protected String getRaw(String key) {
        delegate.get(key)
    }

    @Override
    V get(String key) {
        final result = delegate.get(key0(key))
        return result ? deserialize(result) : null
    }

    void put(String key, V value) {
        delegate.put(key0(key), serialize(value), getDuration())
    }

    @Override
    void put(String key, V value, Duration ttl) {
        delegate.put(key0(key), serialize(value), ttl)
    }

    @Override
    boolean putIfAbsent(String key, V value, Duration ttl) {
        delegate.putIfAbsent(key0(key), serialize(value), ttl)
    }

    boolean putIfAbsent(String key, V value) {
        delegate.putIfAbsent(key0(key), serialize(value), getDuration())
    }

    @Override
    void remove(String key) {
        delegate.remove(key0(key))
    }

    void clear() {
        delegate.clear()
    }
}
