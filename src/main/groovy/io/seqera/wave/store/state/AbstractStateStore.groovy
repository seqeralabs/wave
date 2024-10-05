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

package io.seqera.wave.store.state

import java.time.Duration

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.store.state.impl.StateProvider
/**
 * Implements a generic store for ephemeral state data
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class AbstractStateStore<V> implements StateStore<String,V> {

    private EncodingStrategy<V> encodingStrategy

    private StateProvider<String,String> delegate

    AbstractStateStore(StateProvider<String,String> provider, EncodingStrategy<V> encodingStrategy) {
        this.delegate = provider
        this.encodingStrategy = encodingStrategy
    }

    protected abstract String getPrefix()

    protected abstract Duration getDuration()

    protected String key0(String k) { return getPrefix() + k  }

    protected String requestId0(String requestId) {
        return getPrefix() + 'request-id/' + requestId
    }

    protected String counterKey(String key, V value) {
        return key
    }

    protected V deserialize(String encoded) {
        return encodingStrategy.decode(encoded)
    }

    protected String serialize(V value) {
        return encodingStrategy.encode(value)
    }


    @Override
    V get(String key) {
        final result = delegate.get(key0(key))
        return result ? deserialize(result) : null
    }

    V findByRequestId(String requestId) {
        final key = delegate.get(requestId0(requestId))
        return get(key)
    }

    @Override
    void put(String key, V value) {
        put(key, value, getDuration())
    }

    @Override
    void put(String key, V value, Duration ttl) {
        delegate.put(key0(key), serialize(value), ttl)
        if( value instanceof RequestIdAware ) {
            delegate.put(requestId0(value.getRequestId()), key, ttl)
        }
    }

    @Override
    boolean putIfAbsent(String key, V value, Duration ttl) {
        final result = delegate.putIfAbsent(key0(key), serialize(value), ttl)
        if( result && value instanceof RequestIdAware ) {
            delegate.put(requestId0(value.getRequestId()), key, ttl)
        }
        return result
    }

    @Override
    boolean putIfAbsent(String key, V value) {
        return putIfAbsent(key, value, getDuration())
    }

    Tuple3<Boolean,V,Integer> putIfAbsentAndCount(String key, V value) {
        putIfAbsentAndCount(key, value, getDuration())
    }

    Tuple3<Boolean,V,Integer> putIfAbsentAndCount(String key, V value, Duration ttl) {
        final String reqId
        final result = delegate.putIfAbsent(key0(key), serialize(value), ttl, counterKey(key,value))
        if( result && value instanceof RequestIdAware && (reqId=value.getRequestId()) ) {
            delegate.put(requestId0(reqId), key, ttl)
        }
        return new Tuple3<Boolean, V, Integer>(
                result.v1,
                deserialize(result.v2),
                result.v3)
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
