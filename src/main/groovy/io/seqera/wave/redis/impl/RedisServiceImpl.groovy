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

package io.seqera.wave.redis.impl

import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.redis.RedisService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.Transaction
import redis.clients.jedis.params.ScanParams
import redis.clients.jedis.params.SetParams
import redis.clients.jedis.params.XAutoClaimParams
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.resps.ScanResult
import redis.clients.jedis.resps.StreamEntry
import redis.clients.jedis.resps.Tuple
/**
 * Implements RedisService for standalone redis
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(property = 'redis.uri')
@Requires(property = "redis.mode", notEquals = "cluster")
@CompileStatic
class RedisServiceImpl implements RedisService {

    @Inject
    private JedisPool pool

    @Override
    String get(String key) {
        try( Jedis conn=pool.getResource() ) {
            return conn.get(key)
        }
    }

    @Override
    long hincrBy(final String key, final String field, final long value) {
        try(Jedis conn=pool.getResource() ) {
            return conn.hincrBy(key, field, value)
        }
    }

    @Override
    Long hget(final String key, final String field) {
        try (Jedis conn = pool.getResource()) {
            return conn.hget(key, field) ? conn.hget(key, field).toLong() : null
        }
    }

    @Override
    ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        try(Jedis conn=pool.getResource() ) {
            return conn.hscan(key, cursor, params)
        }
    }

    @Override
    String set(final String key, final String value, final SetParams params) {
        try (Jedis conn = pool.getResource()) {
            return conn.set(key, value, params)
        }
    }

    @Override
    Transaction multi() throws TimeoutException {
        try (Jedis conn = pool.getResource()) {
            return conn.multi()
        }
    }

    @Override
    long lpush(final String target, final String message) {
        try (Jedis conn = pool.getResource()) {
            return conn.lpush(target, message)
        }
    }

    @Override
    String rpop(final String target) {
        try (Jedis conn = pool.getResource()) {
            return conn.rpop(target)
        }
    }

    @Override
    String brpop(final double timeout, final String target) {
        try (Jedis conn = pool.getResource()) {
            return conn.brpop(timeout, target)
        }
    }

    @Override
    String xgroupCreate(final String key, final String groupName, final StreamEntryID id, final boolean makeStream) {
        try (Jedis conn = pool.getResource()) {
            return conn.xgroupCreate(key, groupName, id, makeStream)
        }
    }

    @Override
    StreamEntryID xadd(final String key, final StreamEntryID id, final Map<String, String> hash) {
        try (Jedis conn = pool.getResource()) {
            return conn.xadd(key, id, hash)
        }
    }

    @Override
    Map.Entry<StreamEntryID, List<StreamEntry>> xautoclaim(String key, String group, String consumerName, long minIdleTime, StreamEntryID start, XAutoClaimParams params) {
        try (Jedis conn = pool.getResource()) {
            return conn.xautoclaim(key, group, consumerName, minIdleTime, start, params)
        }
    }

    @Override
    List<Map.Entry<String, List<StreamEntry>>> xreadGroup(final String groupName, final String consumer, final XReadGroupParams xReadGroupParams, final Map<String, StreamEntryID> streams) {
        try (Jedis conn = pool.getResource()) {
            return conn.xreadGroup(groupName, consumer, xReadGroupParams, streams)
        }
    }

    @Override
    long zadd(final String key, final double score, final String member) {
        try (Jedis conn = pool.getResource()) {
            return conn.zadd(key, score, member)
        }
    }

    @Override
    Object eval(final String script, final int keyCount, final String... params) {
        try (Jedis conn = pool.getResource()) {
            return conn.eval(script, keyCount, params)
        }
    }

    @Override
    List<Tuple> zrangeByScoreWithScores(final String key, final double min, final double max, final int offset, final int count) {
        try (Jedis conn = pool.getResource()) {
            return conn.zrangeByScoreWithScores(key, min, max, offset, count)
        }
    }

    @Override
    long del(String key) {
        try (Jedis conn = pool.getResource()) {
            return conn.del(key)
        }
    }

    @Override
    String flushAll() {
        try (Jedis conn = pool.getResource()) {
            return conn.flushAll()
        }
    }
}
