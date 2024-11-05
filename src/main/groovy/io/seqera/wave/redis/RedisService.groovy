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

package io.seqera.wave.redis

import java.util.concurrent.TimeoutException

import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.Transaction
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
interface RedisService {

    long hincrBy(final String key, final String field, final long value)

    Long hget(final String key, final String field)

    ScanResult<Map. Entry<String, String>> hscan(final String key, final String pattern)

    String set(final String key, final String value, final SetParams params)

    Transaction multi() throws TimeoutException

    long lpush(final String target, final String message)

    String rpop(final String target)

    String brpop( final double timeout, final String target)

    String xgroupCreate(final String key, final String groupName, final StreamEntryID id, final boolean makeStream)

    StreamEntryID xadd(final String key, final StreamEntryID id, final Map<String, String> hash)

    Map.Entry<StreamEntryID, List<StreamEntry>> xautoclaim(String key, String group, String consumerName, long minIdleTime, StreamEntryID start, XAutoClaimParams params)

    List<Map.Entry<String, List<StreamEntry>>> xreadGroup(final String groupName, final String consumer, final XReadGroupParams xReadGroupParams, final Map<String, StreamEntryID> streams)

    long zadd(final String key, final double score, final String member)

    Object eval(final String script, final int keyCount, final String... params)

    List<Tuple> zrangeByScoreWithScores(final String key, final double min, final double max, final int offset, final int count)

    long del(final String key)

    String flushAll()

}
