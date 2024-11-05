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
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.redis.RedisService
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
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
 * Implements RedisService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(property = 'redis.uri')
@CompileStatic
class RedisServiceImpl implements RedisService {

    private JedisPool pool

    private JedisCluster cluster

    @Value('${redis.hscan.count:10000}')
    private Integer hscanCount

    RedisService(@Nullable JedisPool jedisPool, @Nullable JedisCluster jedisCluster) {
        this.pool = jedisPool
        this.cluster = jedisCluster

        if( pool != null ) {
            log.info "Using redis standalone as storage and cache"
        } else if( cluster != null ) {
            log.info "Using redis cluster as storage and cache"
        } else {
            throw new IllegalArgumentException("No redis connection pool found")
        }
    }

    @Override
    long hincrBy(final String key, final String field, final long value) {
        if ( pool != null ){
            try(Jedis conn=pool.getResource() ) {
                return conn.hincrBy(key, field, value)
            }
        } else {
            return cluster.hincrBy(key, field, value)
        }
    }

    @Override
    Long hget(final String key, final String field) {
        if (pool != null) {
            try (Jedis conn = pool.getResource()) {
                return conn.hget(key, field) ? conn.hget(key, field).toLong() : null
            }
        } else if (cluster != null) {
            return cluster.hget(key, field) ? cluster.hget(key, field).toLong() : null
        }
    }

    @Override
    ScanResult<Map. Entry<String, String>> hscan(final String key, final String pattern) {
        if ( pool != null ){
            try(Jedis conn=pool.getResource() ) {
                return conn.hscan(key, "0", new ScanParams().match(pattern).count(hscanCount))
            }
        } else if ( cluster != null ){
            return cluster.hscan(key, "0", new ScanParams().match(pattern).count(hscanCount))
        }
    }

    @Override
    String set(final String key, final String value, final SetParams params) {
        if ( pool != null ){
            try (Jedis conn = pool.getResource()) {
                conn.set(key, value, params)
            }
        } else if ( cluster != null ){
            cluster.set(key, value, params)
        }
    }

    @Override
    Transaction multi() throws TimeoutException {
        if ( pool != null ){
            try (Jedis conn = pool.getResource()) {
                return conn.multi()
            }
        } else if( cluster != null ){
            return cluster.multi()
        }
    }

    @Override
    long lpush(final String target, final String message) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                conn.lpush(target, message)
            }
        } else if ( cluster != null ) {
            cluster.lpush(target, message)
        }
    }

    @Override
    String rpop(final String target) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.rpop(target)
            }
        } else if ( cluster != null ) {
            return cluster.rpop(target)
        }
    }

    @Override
    String brpop(final double timeout, final String target) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.brpop(timeout, target)
            }
        } else if ( cluster != null ) {
            return cluster.brpop(timeout, target)
        }
    }

    @Override
    String xgroupCreate(final String key, final String groupName, final StreamEntryID id, final boolean makeStream) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.xgroupCreate(key, groupName, id, makeStream)
            }
        } else if ( cluster != null ) {
            return cluster.xgroupCreate(key, groupName, id, makeStream)
        }
    }

    @Override
    StreamEntryID xadd(final String key, final StreamEntryID id, final Map<String, String> hash) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.xadd(key, id, hash)
            }
        } else if ( cluster != null ) {
            return cluster.xadd(key, id, hash)
        }
    }

    @Override
    Map.Entry<StreamEntryID, List<StreamEntry>> xautoclaim(String key, String group, String consumerName, long minIdleTime, StreamEntryID start, XAutoClaimParams params) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.xautoclaim(key, group, consumerName, minIdleTime, start, params)
            }
        } else if ( cluster != null ) {
            return cluster.xautoclaim(key, group, consumerName, minIdleTime, start, params)
        }
    }

    @Override
    List<Map.Entry<String, List<StreamEntry>>> xreadGroup(final String groupName, final String consumer, final XReadGroupParams xReadGroupParams,
                                                                 final Map<String, StreamEntryID> streams) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.xreadGroup(groupName, consumer, xReadGroupParams, streams)
            }
        } else if ( cluster != null ) {
            return cluster.xreadGroup(groupName, consumer, xReadGroupParams, streams)
        }
    }

    @Override
    long zadd(final String key, final double score, final String member) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.zadd(key, score, member)
            }
        } else if ( cluster != null ) {
            return cluster.zadd(key, score, member)
        }
    }

    @Override
    Object eval(final String script, final int keyCount, final String... params) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.eval(script, keyCount, params)
            }
        } else if ( cluster != null ) {
            cluster.eval(script, keyCount, params)
        }
    }

    @Override
    List<Tuple> zrangeByScoreWithScores(final String key, final double min, final double max, final int offset, final int count) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.zrangeByScoreWithScores(key, min, max, offset, count)
            }
        } else if ( cluster != null ) {
            return cluster.zrangeByScoreWithScores(key, min, max, offset, count)
        }
    }

    @Override
    long del(String key) {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.del(key)
            }
        } else if ( cluster != null ) {
            return cluster.del(key)
        }
    }

    @Override
    String flushAll() {
        if ( pool != null ) {
            try (Jedis conn = pool.getResource()) {
                return conn.flushAll()
            }
        } else if ( cluster != null ) {
            return cluster.flushAll()
        }
    }
}
