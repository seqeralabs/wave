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

/**
 * Interface for cache store operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CacheStore<K,V> {

    /**
     * Retrieve a cached object by the given key
     *
     * @param key
     *      The key of the object to be retrieved
     * @return
     *      The object matching the specified key, or {@code null} if no object exists
     */
    V get(K key)

    /**
     * Store a the specified key-value pair in the underlying cache
     *
     * @param key The key to retrieve the associated value
     * @param value The value to be store in the cache
     * @param ttl The max time-to-live of the stored entry
     */
    void put(K key, V value, Duration ttl)

    /**
     * Store a value in the cache only if does not exist yet
     * @param key The unique associated with this object
     * @param value The object to store
     * @param ttl The max time-to-live of the stored entry
     * @return {@code true} if the value was stored, {@code false} otherwise
     */
    boolean putIfAbsent(K key, V value, Duration ttl)


    /**
     * Store a value in the cache only if does not exist yet
     * and returns the value that is in the cache after the
     * operation.
     *
     * @param key The unique key associated with this object
     * @param value The object to store
     * @param ttl The max time-to-live of the stored entry
     * @return The current value in the cache, that is the existing one
     *         if already present or the new value otherwise
     */
    V putIfAbsentAndGetCurrent(K key, V value, Duration ttl)

    /**
     * Remove the entry with the specified key from the cache
     *
     * @param key The key of the entry to be removed
     */
    void remove(K key)

    /**
     * Remove all entries from the cache
     */
    void clear()

}
