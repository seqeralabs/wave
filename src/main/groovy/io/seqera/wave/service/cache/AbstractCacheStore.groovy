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

    AbstractCacheStore(CacheProvider<String,String> provider, EncodingStrategy<V> encodingStrategy) {
        this.delegate = provider
        this.encodingStrategy = encodingStrategy
    }

    protected abstract String getPrefix()

    protected abstract Duration getDuration()

    protected String key0(String k) { return getPrefix() + k  }

    protected String recordId0(String recordId) {
        return getPrefix() + 'state-id/' + recordId
    }

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

    V getByRecordId(String recordId) {
        final key = delegate.get(recordId0(recordId))
        return get(key)
    }

    void put(String key, V value) {
        put(key, value, getDuration())
    }

    @Override
    void put(String key, V value, Duration ttl) {
        delegate.put(key0(key), serialize(value), ttl)
        if( value instanceof StateRecord ) {
            delegate.put(recordId0(value.getRecordId()), key, ttl)
        }
    }

    @Override
    boolean putIfAbsent(String key, V value, Duration ttl) {
        final result = delegate.putIfAbsent(key0(key), serialize(value), ttl)
        if( result && value instanceof StateRecord ) {
            delegate.put(recordId0(value.getRecordId()), key, ttl)
        }
        return result
    }

    boolean putIfAbsent(String key, V value) {
        return putIfAbsent(key, value, getDuration())
    }

    @Override
    void remove(String key) {
        delegate.remove(key0(key))
    }

    @Override
    void clear() {
        delegate.clear()
    }

}
