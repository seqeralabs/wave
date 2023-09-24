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

package io.seqera.wave.service.data.future

import java.time.Duration
/**
 * Define the interface for a future distributed hash
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureHash<V> {

    /**
     * Add a value in the distributed "queue". The value is evicted after
     * the specified expired duration
     *
     * @param key The key associated with the provided value
     * @param value The value to be stored.
     * @param expiration The amount of time after which the value is evicted
     */
    void put(String key, V value, Duration expiration)

    /**
     * Get the value with the specified key
     *
     * @param key The key of the value to be taken
     * @return The value associated with the specified key or {@code null} otherwise
     */
    V take(String key)

}
