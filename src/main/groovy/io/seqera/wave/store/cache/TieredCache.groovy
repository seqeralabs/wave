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

package io.seqera.wave.store.cache
/**
 * Base interface for tiered-cache system
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface TieredCache<K,V> {

    /**
     * Retrieve the value associated with the specified key
     *
     * @param key The key of the value to be retrieved
     * @return The value associated with the specified key, or {@code null} otherwise
     */
    V get(K key)

    /**
     * Add a value in the cache with the specified key. If a value already exists is overridden
     * with the new value.
     *
     * @param key The key of the value to be added. {@code null} is not allowed.
     * @param value The value to be added in the cache for the specified key.  {@code null} is not allowed.
     */
    void put(K key, V value)

}
