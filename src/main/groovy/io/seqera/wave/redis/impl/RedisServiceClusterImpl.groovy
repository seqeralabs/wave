package io.seqera.wave.redis.impl

import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.redis.RedisService
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.JedisCluster
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
 * Implements RedisService for redis cluster
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(property = 'redis.uri')
@Requires(property = "redis.mode", value = "cluster")
@CompileStatic
class RedisServiceClusterImpl  implements RedisService {

    @Inject
    private JedisCluster cluster

    @Override
    String get(String key) {
        return cluster.get(key)
    }

    @Override
    long hincrBy(String key, String field, long value) {
        return cluster.hincrBy(key, field, value)
    }

    @Override
    Long hget(String key, String field) {
        return cluster.hget(key, field) ? cluster.hget(key, field).toLong() : null
    }

    @Override
    ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        return cluster.hscan(key, cursor, params)
    }

    @Override
    String set(String key, String value, SetParams params) {
        return cluster.set(key, value, params)
    }

    @Override
    Transaction multi() throws TimeoutException {
        return cluster.multi()
    }

    @Override
    long lpush(String target, String message) {
        return cluster.lpush(target, message)
    }

    @Override
    String rpop(String target) {
        return cluster.rpop(target)
    }

    @Override
    String brpop(double timeout, String target) {
        return cluster.brpop(timeout, target)
    }

    @Override
    String xgroupCreate(String key, String groupName, StreamEntryID id, boolean makeStream) {
        return cluster.xgroupCreate(key, groupName, id, makeStream)
    }

    @Override
    StreamEntryID xadd(String key, StreamEntryID id, Map<String, String> hash) {
        return cluster.xadd(key, id, hash)
    }

    @Override
    Map.Entry<StreamEntryID, List<StreamEntry>> xautoclaim(String key, String group, String consumerName, long minIdleTime, StreamEntryID start, XAutoClaimParams params) {
        return cluster.xautoclaim(key, group, consumerName, minIdleTime, start, params)
    }

    @Override
    List<Map.Entry<String, List<StreamEntry>>> xreadGroup(String groupName, String consumer, XReadGroupParams xReadGroupParams, Map<String, StreamEntryID> streams) {
        return cluster.xreadGroup(groupName, consumer, xReadGroupParams, streams)
    }

    @Override
    long zadd(String key, double score, String member) {
        return cluster.zadd(key, score, member)
    }

    @Override
    Object eval(String script, int keyCount, String... params) {
        return cluster.eval(script, keyCount, params)
    }

    @Override
    List<Tuple> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
        return cluster.zrangeByScoreWithScores(key, min, max, offset, count)
    }

    @Override
    long del(String key) {
        return cluster.del(key)
    }

    @Override
    String flushAll() {
        return cluster.flushAll()
    }
}
