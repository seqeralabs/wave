/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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

package io.seqera.wave.service.data.workqueue

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.data.workqueue.redis.RedisWorkQueueConfig
import jakarta.inject.Singleton

/**
 * Configuration bean for Redis-backed work queues in the Wave application.
 *
 * <p>This configuration provides centralized settings for Redis work-queue
 * components, including consumer group management and timeout
 * configurations. The configuration values are injected from application
 * properties with sensible defaults.
 *
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code wave.message-stream.consumer-group-name} - Name for the Redis consumer group (default: "wave-message-stream")</li>
 *   <li>{@code wave.message-stream.claim-timeout} - Visibility timeout for delivered messages (default: 5s)</li>
 *   <li>{@code wave.message-stream.consume-warn-timeout} - Timeout threshold for consumer warnings (default: 4s)</li>
 * </ul>
 *
 * <p>This bean implements {@link RedisWorkQueueConfig} to provide configuration
 * values to Redis work-queue components throughout the application.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @see io.seqera.data.workqueue.redis.RedisWorkQueueConfig
 */
@CompileStatic
@Singleton
class RedisWorkQueueConfigBean implements RedisWorkQueueConfig {

    @Value('${wave.message-stream.consumer-group-name:wave-message-stream}')
    String defaultConsumerGroupName

    @Value('${wave.message-stream.claim-timeout:5s}')
    Duration visibilityTimeout

    @Value('${wave.message-stream.consume-warn-timeout:4s}')
    Duration consumerWarnTimeout

}
