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

package io.seqera.wave.service.data.future


import java.util.concurrent.CompletableFuture

/**
 * Implements a {@link FutureStore} that allow handling {@link CompletableFuture} objects
 * in a distributed environment.
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureStore<K,V> {

    /**
     * Create a {@link CompletableFuture} object
     *
     * @param key The unique id associated with the future object
     * @return A {@link CompletableFuture} object holding the future result
     */
    CompletableFuture<V> create(K key)

    /**
     * Complete the {@link CompletableFuture} object with the specified key
     *
     * @param key The unique key of the future to complete
     * @param value The value to used to complete the future
     */
    void complete(K key, V value)

}
