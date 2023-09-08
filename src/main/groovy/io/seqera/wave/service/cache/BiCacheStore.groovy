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
