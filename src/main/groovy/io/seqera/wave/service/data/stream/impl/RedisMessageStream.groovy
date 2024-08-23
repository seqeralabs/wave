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

package io.seqera.wave.service.data.stream.impl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.data.stream.MessageStream
import io.seqera.wave.util.LongRndKey
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.exceptions.JedisDataException
import redis.clients.jedis.params.XAutoClaimParams
import redis.clients.jedis.params.XReadGroupParams
import redis.clients.jedis.resps.StreamEntry
/**
 * Implement a distributed {@link MessageStream} backed by a Redis stream.
 * This implementation allows multiple concurrent consumers and guarantee consistency
 * across replicas restart. 
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'redis')
@Singleton
@CompileStatic
class RedisMessageStream implements MessageStream<String> {

    private static final StreamEntryID STREAM_ENTRY_ZERO = new StreamEntryID("0-0")

    private static final String CONSUMER_GROUP_NAME = "wave-message-stream"

    private static final String DATA_FIELD = 'data'

    private final ConcurrentHashMap<String,Boolean> group0 = new ConcurrentHashMap<>()

    @Inject
    private JedisPool pool

    @Value('${wave.message-stream.claim-timeout:10s}')
    private Duration claimTimeout

    private String consumerName

    @PostConstruct
    private void init() {
        consumerName = "consumer-${LongRndKey.rndLong()}"
        log.info "Creating Redis message stream - consumer=${consumerName}; claim-timeout=${claimTimeout}"
    }

    protected void createGroup(Jedis jedis, String stream, String group) {
        // use a concurrent hash map to create it only the very first time
        group0.computeIfAbsent("$stream/$group".toString(),(it)-> createGroup0(jedis,stream,group))
    }

    protected boolean createGroup0(Jedis jedis, String stream, String group) {
        try {
            jedis.xgroupCreate(stream, group, STREAM_ENTRY_ZERO, true)
            return true
        }
        catch (JedisDataException e) {
            if (e.message.contains("BUSYGROUP")) {
                // The group already exists, so we can safely ignore this exception
                log.debug "Redis message stream - consume group=$group alreayd exists"
                return true
            }
            throw e
        }
    }

    @Override
    void offer(String streamId, String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.xadd(streamId, StreamEntryID.NEW_ENTRY, Map.of(DATA_FIELD, message))
        }
    }

    @Override
    boolean consume(String streamId, Predicate<String> consumer) {
        try (Jedis jedis = pool.getResource()) {
            createGroup(jedis, streamId, CONSUMER_GROUP_NAME)
            final entry = claimMessage(jedis,streamId) ?: readMessage(jedis, streamId)
            if( entry && consumer.test(entry.getFields().get(DATA_FIELD)) ) {
                // Acknowledge the job after processing
                jedis.xack(streamId, CONSUMER_GROUP_NAME, entry.getID())
                return true
            }
            else
                return false
        }
    }

    protected StreamEntry readMessage(Jedis jedis, String target) {
        // Create parameters for reading with a group
        final params = new XReadGroupParams()
                // Read one message at a time
                .count(1)

        // Read new messages from the stream using the correct xreadGroup signature
        List<Map.Entry<String, List<StreamEntry>>> messages = jedis.xreadGroup(
                CONSUMER_GROUP_NAME,
                consumerName,
                params,
                Map.of(target, StreamEntryID.UNRECEIVED_ENTRY) )

        final entry = messages?.first()?.value?.first()
        if( entry ) {
            log.debug "Redis stream read entry=$entry"
        }
        return entry
    }

    protected StreamEntry claimMessage(Jedis jedis, String target) {
        // Attempt to claim any pending messages that are idle for more than the threshold
        final params = new XAutoClaimParams()
                // claim one entry at time
                .count(1)
        final messages = jedis.xautoclaim(
                target,
                CONSUMER_GROUP_NAME,
                consumerName,
                claimTimeout.toMillis(),
                STREAM_ENTRY_ZERO,
                params
        )
        final entry = messages?.getValue()?[0]
        if( entry ) {
            log.debug "Redis stream claimed entry=$entry"
        }
        return entry
    }

}
