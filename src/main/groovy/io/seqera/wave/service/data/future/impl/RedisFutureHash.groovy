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
