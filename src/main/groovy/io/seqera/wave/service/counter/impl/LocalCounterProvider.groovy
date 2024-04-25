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

package io.seqera.wave.service.counter.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
/**
 * Local counter for development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalCounterProvider implements CounterProvider {

    private ConcurrentHashMap<String,ConcurrentHashMap<String, AtomicLong>> store = new ConcurrentHashMap<>()

    @Override
    long inc(String key, String field, long value) {
        final result = store.computeIfAbsent(key, (it)-> new ConcurrentHashMap<>())
        return result.computeIfAbsent(field, (it)-> new AtomicLong(0)).addAndGet(value)
    }

    @Override
    Long get(String key, String field) {
        return store.get(key)?.get(field)?.get()
    }

    @Override
    Map<String, Long> getAllMatchingEntries(String key, String pattern) {
        def keyStore = store.get(key)
        def matchingPairs = keyStore.findAll { k, v -> k =~pattern}
        Map<String, Long> result = [:]
        matchingPairs.each { k, v ->
            result.put(k, v as Long)
        }
        return result
    }
}
