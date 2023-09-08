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
