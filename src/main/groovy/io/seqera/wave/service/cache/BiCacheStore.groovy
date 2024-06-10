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

/**
 * A cache store implementing a bi-direction key-value access,
 * it allows retrieving all keys for a given value in time O(2)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BiCacheStore<K,V> {

    /**
     * Add a bi-directional key-value
     *
     * @param key The entry key
     * @param value The entry value
     * @param ttl The entry time-to-live.
     */
    void biPut(K key, V value, Duration ttl)

    /**
     * Remove an entry for the given value.
     *
     * @param key The key of entry to be removed
     */
    void biRemove(K key)

    /**
     * Get all key for the given value.
     *
     * @param value The value for which find corresponding keys
     * @return A set of keys associated with the specified value or an empty set otherwise.
     */
    Set<K> biKeysFor(V value)

    /**
     * Find a key in the cache for the given value.
     *
     * @param value The value for which find corresponding key
     * @param sorted When true, the list of keys is sorted  before getting the first value
     * @return A key associated with the specified value or null if not key is found
     */
    K biKeyFind(V value, boolean sorted)

}
