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

package io.seqera.wave.service.data.future.impl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.future.FutureHash
import jakarta.inject.Singleton
/**
 * Implement a future queue based on a simple hash map.
 * This is only meant for local/development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Singleton
@CompileStatic
class LocalFutureHash implements FutureHash<String> {

    private ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>()

    @Override
    void put(String key, String value, Duration expiration) {
        store.putIfAbsent(key, value)
    }

    @Override
    String take(String key) {
        return store.remove(key)
    }
}
