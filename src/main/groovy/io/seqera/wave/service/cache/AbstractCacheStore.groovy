/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.service.cache

import java.time.Duration

import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.service.cache.impl.CacheProvider

/**
 * Implements a generic cache store
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class AbstractCacheStore<V> implements CacheStore<String,V>, BiCacheStore<String,V> {

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

    protected String serialize(V value) {
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
    V putIfAbsentAndGetCurrent(String key, V value, Duration ttl) {
        final result = delegate.putIfAbsentAndGetCurrent(key0(key), serialize(value), ttl)
        return result? deserialize(result) : null
    }

    V putIfAbsentAndGetCurrent(String key, V value) {
        return putIfAbsentAndGetCurrent(key, value, getDuration())
    }

    @Override
    void remove(String key) {
        delegate.remove(key0(key))
    }

    @Override
    void clear() {
        delegate.clear()
    }

    @Override
    void biPut(String key, V value, Duration ttl) {
        delegate.biPut(key0(key), serialize(value), ttl)
    }

    @Override
    void biRemove(String key) {
        delegate.biRemove(key0(key))
    }

    @Override
    Set<String> biKeysFor(V value) {
        final keys = delegate.biKeysFor(serialize(value))
        return keys.collect( (it) -> it.replace(getPrefix(),'') )
    }

    @Override
    String biKeyFind(V value, boolean sorted) {
        final result = delegate.biKeyFind(serialize(value), sorted)
        result ? result.replace(getPrefix(),'') : null
    }
}
