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

package io.seqera.wave.service.cache.impl

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Simple cache store implementation for development purpose
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalCacheProvider implements CacheProvider<String,String> {

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

    void put(String key, String value) {
        store.put(key, new Entry<>(value,null))
    }

    @Override
    void put(String key, String value, Duration ttl) {
        store.put(key, new Entry<>(value,ttl))
    }

    boolean putIfAbsent(String key, String value) {
        return putIfAbsent0(key, value, null) == null
    }

    @Override
    boolean putIfAbsent(String key, String value, Duration ttl) {
        return putIfAbsent0(key, value, ttl) == null
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
