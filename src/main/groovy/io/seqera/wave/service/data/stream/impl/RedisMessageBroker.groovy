package io.seqera.wave.service.data.stream.impl

import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.retry.annotation.Retryable
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.data.stream.MessageBroker
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.StreamEntry
import redis.clients.jedis.StreamEntryID
import redis.clients.jedis.exceptions.JedisException
import static redis.clients.jedis.params.XAutoClaimParams.xAutoClaimParams
import static redis.clients.jedis.params.XReadGroupParams.xReadGroupParams

/**
 * An implementation of the {@link MessageBroker} interface that uses Redis to provide distributed message brokering capabilities.
 * This class provides methods to send messages to a Redis stream and to register/unregister consumers for the same stream.
 *
 * This implementation uses Redis XREADGROUP and XADD commands to read messages from a Redis stream and to add messages to it,
 * respectively. It also creates a Redis consumer group for each stream and registers consumers as members of the group.
 * When a new message is added to the stream, Redis will distribute it to one consumer in the consumer group.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Requires(env = 'redis')
@Singleton
@CompileStatic
class RedisMessageBroker implements MessageBroker<String> {

    @Value('${wave.streams.block:5s}')
    Duration blockTimeout

    /**
     * Consumers need to be periodically registered before this timeout to consider them active
     */
    @Value('${wave.streams.expire:60s}')
    Duration expireTimeout

    /**
     * Message idle for more than this timeout are eligible to be reclaimed for another consumer
     */
    @Value('${wave.streams.reclaim:20s}')
    Duration reclaimTimeout

    /**
     * The Redis stream won't persist more than 'maxLen' messages
     */
    @Value('${wave.streams.maxlen:100}')
    Long maxLen

    @Inject
    private JedisPool pool
    
    @Canonical
    static class ConsumerKey {
        String streamKey
        String consumerId
    }


    private ExecutorService executorService
    private String consumerGroup
    private Cache<ConsumerKey, RedisConsumer> consumers


    /**
     * An inner class that represents a Redis consumer. This class is responsible for reading messages
     * from the Redis stream and passing them to the consumer registered for the same stream.
     */
    class RedisConsumer implements Runnable {

        private String streamKey
        private String consumerId
        private Consumer<String> consumer
        private boolean registered

        /**
         * Create a new RedisConsumer.
         *
         * @param streamKey the key of the Redis stream to consume from
         * @param consumer the consumer function that will process the messages
         */
        RedisConsumer(String streamKey, Consumer<String> consumer) {
            this.streamKey = streamKey
            this.consumer = consumer
            this.registered = true
        }

        /**
         * Unregisters this consumer from the Redis consumer group.
         */
        void unregister() {
            this.registered = false
        }

        /**
         * Read messages from Redis and consume them using the registered consumer.
         */
        @Override
        void run() {
            while (registered) {
                try (Jedis conn = pool.getResource()) {

                    // Read messages from the stream
                    List<StreamEntry> messages = readPendingMessages(conn)

                    if (!messages || messages.isEmpty()) {
                        continue
                    }

                    // Process each message
                    for (message in messages) {
                        consumer.accept(message.fields["message"] as String)
                        conn.xack(streamKey, consumerGroup, message.ID)
                    }
                } catch (Exception e) {
                    log.error "while consumer '$consumerId' was processing a message from '$streamKey' -- ${e.message}"
                    unregisterConsumer(streamKey, consumerId)
                    return
                }
            }

            // Delete the group consumer
            try (Jedis conn = pool.getResource()) {
                conn.xgroupDelConsumer(streamKey, consumerGroup, consumerId)
            }
        }

        /**
         * Reads the pending messages from a specific stream key and consumer group by attempting to reclaim them.
         * If there are no pending messages, the function reads a single message from the stream key.
         *
         * @param conn the Jedis connection to use
         * @return a List containing the reclaimed or newly received messages, or NULL if no messages were found
         */
        protected List<StreamEntry> readPendingMessages(Jedis conn) {
            // check non-ack messages an claim them
            final pending = conn.xautoclaim(
                    streamKey,
                    consumerGroup,
                    consumerId,
                    reclaimTimeout.toMillis(),
                    new StreamEntryID(0, 0),
                    xAutoClaimParams().count(10)
            )

            // return reclaimed messages
            if (!pending?.value?.empty)
                return pending.value

            // otherwise check for new unreceived messages
            final results = conn.xreadGroup(
                    consumerGroup,
                    consumerId,
                    xReadGroupParams().count(1).block(blockTimeout.toMillis() as int),
                    Map.of(streamKey, StreamEntryID.UNRECEIVED_ENTRY)
            )
            for (entry in results) {
                if (entry.key == streamKey)
                    return entry.value
            }

            // nothing found
            return null
        }
    }

    /**
     * This method initializes the consumer group name, creates a new executor service, and a new
     * cache to store the registered consumers that expires after an 'expireTimeout'.
     */
    @PostConstruct
    void init() {
        this.consumerGroup = "redis-message-broker-consumers"
        this.executorService = Executors.newCachedThreadPool()
        this.consumers = CacheBuilder.newBuilder().expireAfterAccess(expireTimeout).build()
    }

    /**
     * Create a stream consumer group if it doesn't exists
     *
     * @param streamKey the key of the Redis stream to create consumer group
     */
    @Retryable(includes = JedisException, multiplier = "2.0")
    void createConsumerGroup(String streamKey) {
        try (Jedis conn = pool.getResource()) {
            for (group in conn.xinfoGroup(streamKey)) {
                if (group.name == consumerGroup) {
                    // consumer group already exists
                    return
                }
            }
        } catch (JedisException e) {
            log.warn "checking consumer group of '$streamKey' -- ${e.message}"
        }

        try (Jedis conn = pool.getResource()) {
            conn.xgroupCreate(streamKey, consumerGroup, StreamEntryID.LAST_ENTRY, true)
        }
    }


    /**
     * Send a message to a Redis stream.
     *
     * @param streamKey the key of the Redis stream to send the message to
     * @param message the message to send
     */
    @Override
    void sendMessage(String streamKey, String message) {
        try (Jedis conn = pool.getResource()) {
            conn.xadd(streamKey, StreamEntryID.NEW_ENTRY, ["message": message], maxLen, true)
        }
    }

    /**
     * Registers a consumer for the given stream key with the specified consumer ID and message consumer.
     * If the consumer ID is already registered, this method only refresh the expiration consumer timeout.
     *
     * @param streamKey the key of the stream to register the consumer for
     * @param consumerId the ID of the consumer to register
     * @param messageConsumer the consumer that will receive messages from the stream
     */
    @Override
    void registerConsumer(String streamKey, String consumerId, Consumer<String> consumer) {
        final key = new ConsumerKey(streamKey, consumerId)

        // Skip already registered consumers and refresh expiration timeout
        if (consumers.getIfPresent(key) != null) {
            return
        }

        // Create the consumer group if it doesn't exist
        createConsumerGroup(streamKey)

        // Start the consumer
        final redisConsumer = new RedisConsumer(streamKey, consumer)
        consumers.put(key, redisConsumer)
        executorService.submit(redisConsumer)

    }

    /**
     * Unregisters a consumer for the given stream key with the specified consumer ID.
     * If the consumer ID is not registered, this method throws a BadRequestException.
     *
     * @param streamKey the key of the stream to unregister the consumer from
     * @param consumerId the ID of the consumer to unregister
     * @throws BadRequestException if the consumer ID is not registered for the given stream key
     */
    @Override
    void unregisterConsumer(String streamKey, String consumerId) {
        final key = new ConsumerKey(streamKey, consumerId)
        final consumer = consumers.getIfPresent(key)
        if (!consumer) {
            log.error "no consumer id=${consumerId} streamKey=${streamKey}"
            return
        }
        consumer.unregister()
    }

    /**
     * Checks if there are any consumers registered for the given stream key.
     *
     * @param streamKey the key of the stream to check for registered consumers
     * @return true if there are any consumers registered for the stream key, false otherwise
     */
    @Override
    boolean hasConsumer(String streamKey) {
        try (Jedis conn = pool.getResource()) {
            return conn.xinfoConsumers(streamKey, consumerGroup).size() > 0
        } catch (JedisException e) {
            log.error "checking if stream '$streamKey' has consumers on group '$consumerGroup'"
            return false
        }
    }

}
