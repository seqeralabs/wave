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

package io.seqera.wave.memstore.range.impl


import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.resps.Tuple

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(property = 'redis.uri')
@Singleton
@CompileStatic
class RedisRangeProvider implements RangeProvider {

    @Inject
    private JedisPool pool

    @Override
    void add(String key, String element, double score) {
        try(Jedis conn = pool.getResource()) {
            conn.zadd(key, score, element)
        }
    }

    @Override
    List<String> getRange(String key, double min, double max, int count, boolean remove) {
        try(Jedis conn = pool.getResource()) {
            List<Tuple> found = conn.zrangeByScoreWithScores(key, min, max, 0, count)
            final result = new ArrayList<String>(found.size())
            for( Tuple it : found ) {
                result.add(it.element)
                if( remove )
                    conn.zrem(key, it.element)
            }
            return result
        }
    }
}
