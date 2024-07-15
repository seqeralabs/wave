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

package io.seqera.wave.service.counter.impl

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.ScanParams
/**
 * Implement a counter based on Redis cache
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(property = 'redis.uri')
@Singleton
@CompileStatic
class RedisCounterProvider implements CounterProvider {

    @Inject
    private JedisPool pool

    @Value('${redis.hscan.count:10000}')
    private Integer hscanCount

    @Override
    long inc(String key, String field, long value) {
        try(Jedis conn=pool.getResource() ) {
            return conn.hincrBy(key, field, value)
        }
    }

    @Override
    Long get(String key, String field) {
        try(Jedis conn=pool.getResource() ) {
            return conn.hget(key, field) ? conn.hget(key, field).toLong() : null
        }
    }

    @Override
    Map<String, Long> getAllMatchingEntries(String key, String pattern) {
        try(Jedis conn=pool.getResource() ) {
            final scanResult = conn.hscan(key, "0", new ScanParams().match(pattern).count(hscanCount))
            if( !scanResult )
                return Map.<String, Long>of()
            final result = new HashMap<String, Long>()
            for(String entry : scanResult.result) {
                final parts = entry.tokenize('=')
                result.put(parts[0], parts[1] as Long)
            }
            return result
        }
    }

    @Override
    void deleteAllMatchingEntries(String key, String pattern) {
        try(Jedis conn=pool.getResource() ) {
            final scanResult = conn.hscan(key, "0", new ScanParams().match(pattern).count(hscanCount))
            if( !scanResult )
                return
            for(String entry : scanResult.result) {
                final parts = entry.tokenize('=')
                conn.hdel(key, parts[0])
            }
        }
    }
}
