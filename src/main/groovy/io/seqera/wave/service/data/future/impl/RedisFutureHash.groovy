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
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.future.FutureHash
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams

/**
 * Implements a future queue using Redis hash. The hash was chosen over
 * a Redis list, because values that fail to be collected within the
 * expected timout, are evicted by Redis by simply specifying the hash expiration.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'redis')
@Singleton
@CompileStatic
class RedisFutureHash implements FutureHash<String>  {

    @Inject
    private JedisPool pool

    @Override
    void put(String key, String value, Duration expiration) {
        try (Jedis conn = pool.getResource()) {
            final params = new SetParams().ex(expiration.toSeconds())
            conn.set(key, value, params)
        }
    }

    @Override
    String take(String key) throws TimeoutException {
        try (Jedis conn = pool.getResource()) {
            /*
             * get and remove the value using an atomic operation
             */
            final tx = conn.multi()
            final result = tx.get(key)
            tx.del(key)
            tx.exec()
            return result.get()
        }
    }

}
