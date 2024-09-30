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

import io.seqera.wave.store.state.StateStore
/**
 * Define an cache interface alias to be used by cache implementation providers
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface StateProvider<K,V> extends StateStore<K,V> {

    /**
     * Store a value in the cache only if does not exist. If the operation is successful
     * the counter identified by the key specified is incremented by 1 and the counter (new)
     * value is returned as result the operation.
     *
     * @param key
     *      The unique associated with this object
     * @param value
     *      The object to store
     * @param counterKey
     *      The counter unique key to be incremented
     * @param ttl
     *      The max time-to-live of the stored entry
     * @return
     *      A tuple with 3 elements with the following semantic: <result, value, count>, where "result" is {@code true}
     *      when the value was actually updated or {@code false} otherwise. "value" represent the specified value when
     *      "return" is true or the value currently existing if the key already exist. Finally "count" is the value
     *      of the count after the increment operation.
     */
    Tuple3<Boolean,V,Integer> putIfAbsent(K key, V value, Duration ttl, String counterKey)

}
