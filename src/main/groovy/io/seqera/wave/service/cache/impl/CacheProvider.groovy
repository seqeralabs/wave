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

package io.seqera.wave.service.cache.impl

import io.seqera.wave.service.cache.BiCacheStore
import io.seqera.wave.service.cache.CacheStore

/**
 * Define an cache interface alias to be used by cache implementation providers
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CacheProvider<K,V> extends CacheStore<K,V>, BiCacheStore<K,V> {
}
