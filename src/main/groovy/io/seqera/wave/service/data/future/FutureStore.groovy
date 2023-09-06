/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
