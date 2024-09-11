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
 * Redis base implementation for range set
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

    private final static String SCRIPT = '''
        local elements = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2], 'LIMIT', ARGV[3], ARGV[4])  
        if #elements > 0 then
            redis.call('ZREM', KEYS[1], unpack(elements)) 
        end
        return elements
        '''

    @Override
    List<String> getRange(String key, double min, double max, int count, boolean remove) {
        try(Jedis conn = pool.getResource()) {
            final result = new ArrayList<String>()
            if( remove ) {
                final entries = conn.eval(SCRIPT, 1, key, min.toString(), max.toString(), '0', count.toString())
                if( entries instanceof List )
                    result.addAll(entries)
            }
            else {
                List<Tuple> found = conn.zrangeByScoreWithScores(key, min, max, 0, count)
                for( Tuple it : found ) {
                    result.add(it.element)
                }
            }
            return result
        }
    }
}
