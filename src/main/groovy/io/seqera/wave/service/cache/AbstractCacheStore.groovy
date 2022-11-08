package io.seqera.wave.service.cache


import java.time.Duration

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.service.cache.impl.CacheProvider

/**
 * Implements a generic cache store
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class AbstractCacheStore<V> implements CacheStore<String,V> {

    private EncodingStrategy<V> encodingStrategy
    private CacheProvider<String,String> delegate

    AbstractCacheStore(CacheProvider<String,String> delegate,
                       EncodingStrategy<V> encodingStrategy){
        this.encodingStrategy = encodingStrategy
        this.delegate = delegate
    }

    protected abstract String getPrefix()

    protected abstract Duration getTimeout()

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
       put(key, value, timeout)
    }

    @Override
    boolean putIfAbsent(String key, V value) {
        putIfAbsent(key, value, timeout)
    }

    @Override
    void put(String key, V value, Duration ttl) {
        delegate.put(key0(key), serialize(value), ttl)
    }

    @Override
    boolean putIfAbsent(String key, V value, Duration ttl) {
        delegate.putIfAbsent(key0(key), serialize(value), ttl)
    }

    @Override
    void remove(String key) {
        delegate.remove(key0(key))
    }
}
