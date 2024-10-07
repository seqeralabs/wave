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

package io.seqera.wave.store.state.impl

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.store.state.CountParams
import io.seqera.wave.store.state.CountResult
import jakarta.inject.Singleton
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Simple cache store implementation for development purpose
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalStateProvider implements StateProvider<String,String> {

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

    private Map<String, AtomicInteger> counters = new ConcurrentHashMap<>()

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
        store.put(key, new Entry<>(value,null))
    }

    @Override
    void put(String key, String value, Duration ttl) {
        store.put(key, new Entry<>(value,ttl))
    }

    @Override
    boolean putIfAbsent(String key, String value) {
        return putIfAbsent0(key, value, null) == null
    }

    @Override
    boolean putIfAbsent(String key, String value, Duration ttl) {
        return putIfAbsent0(key, value, ttl) == null
    }

    @Override
    synchronized CountResult<String> putJsonIfAbsentAndIncreaseCount(String key, String json, Duration ttl, CountParams counterKey, String luaScript) {
        final counter = counterKey.key + '/' + counterKey.field
        final done = putIfAbsent0(key, json, ttl) == null
        final addr = counters
                .computeIfAbsent(counter, (it)-> new AtomicInteger())
        if( done ) {
            final count = addr.incrementAndGet()
            // apply the conversion
            Globals globals = JsePlatform.standardGlobals()
            globals.set('value', LuaValue.valueOf(json))
            globals.set('counter_value', LuaValue.valueOf(count))
            LuaValue chunk = globals.load("return $luaScript;");
            LuaValue result = chunk.call();
            // store the result
            put(key, result.toString(), ttl)
            return new CountResult<String>(true, result.toString(), count)
        }
        else
            return new CountResult<String>(false, get(key), addr.get())
    }

    private String putIfAbsent0(String key, String value, Duration ttl) {
        final entry = store.get(key)
        if( entry?.isExpired() )
            store.remove(key)
        return store.putIfAbsent(key, new Entry<>(value,ttl))?.value
    }

    @Override
    void remove(String key) {
        store.remove(key)
    }

    @Override
    void clear() {
        store.clear()
    }

}
