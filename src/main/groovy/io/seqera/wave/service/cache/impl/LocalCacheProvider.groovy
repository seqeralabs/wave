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

    void put(String key, String value, Duration ttl) {
        store.put(key, new Entry<>(value,ttl))
    }

    @Override
    boolean putIfAbsent(String key, String value, Duration ttl) {
        return putIfAbsent0(key, value, ttl) == null
    }

    @Override
    String putIfAbsentAndGetCurrent(String key, String value, Duration ttl) {
        final ret = putIfAbsent0(key, value, ttl)
        return ret!=null ? ret : value
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

    // =============== bi-cache store implementation ===============

    private Map<Integer,Set<String>> index = new HashMap<>()

    @Override
    void biPut(String key, String value, Duration ttl) {
        synchronized (this) {
            this.put(key, value, ttl)
            final id = value.hashCode()
            def set = index.get(id)
            if( set==null ) {
                set=new HashSet<String>()
                index.put(id, set)
            }
            set.add(key)
        }
    }

    @Override
    void biRemove(String key) {
        synchronized (this) {
            final entry = store.remove(key)
            if( !entry )
                return 
            final id = entry.value.hashCode()
            final set = index.get(id)
            if( set ) {
                set.remove(key)
            }
        }
    }

    @Override
    Set<String> biKeysFor(String value) {
        final id = value.hashCode()
        return index.get(id) ?: Set.<String>of()
    }

    String biKeyFind(String value, boolean sorted) {
        final id = value.hashCode()
        final list = biKeysFor(value).toList()
        final keys = sorted ? list.toSorted() : list.shuffled()
        final itr = keys.iterator()
        while( itr.hasNext() ) {
            final result = itr.next()
            // verify the key still exists
            if( get(result)!=null )
                return result
            // if not exist, remove it from the set
            index.get(id)?.remove(result)
        }
        return null
    }
}
