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
 * Configuration bean for the Redis backed work queue in Wave application.
 *
 * <p>This configuration provides centralized settings for Redis work queue
 * components, including consumer group management and lease timings. The
 * configuration values are injected from application properties with sensible
 * defaults.
 *
 * <p>Configuration properties (each falls back to the legacy {@code wave.message-stream.*}
 * property it replaces, then to the default shown):
 * <ul>
 *   <li>{@code wave.work-queue.consumer-group-name} - Name for the Redis consumer group
 *       (falls back to {@code wave.message-stream.consumer-group-name}; default: "wave-message-stream")</li>
 *   <li>{@code wave.work-queue.visibility-timeout} - How long a claimed message stays invisible to other
 *       consumers before being redelivered (falls back to {@code wave.message-stream.claim-timeout}; default: 45s)</li>
 *   <li>{@code wave.work-queue.consumer-warn-timeout} - Timeout threshold for consumer warnings
 *       (falls back to {@code wave.message-stream.consume-warn-timeout}; default: 45s)</li>
 * </ul>
 *
 * <p>The fallback to the legacy {@code message-stream} keys means a deployment configured for the
 * message-stream implementation keeps its values after the work-queue upgrade with no config change;
 * the final defaults (45s) match the current production deployment.
 *
 * <p>Note the consumer group name default is intentionally {@code wave-message-stream}:
 * the work queue reuses the Redis streams and consumer group created by the message
 * stream implementation it replaces, so messages queued before an upgrade are still
 * delivered afterwards.
 *
 * <p>Example application configuration:
 * <pre>
 * # application.yml
 * wave:
 *   work-queue:
 *     consumer-group-name: "wave-prod-queue"
 *     visibility-timeout: "10s"
 *     consumer-warn-timeout: "8s"
 * </pre>
 *
 * <p>This bean implements {@link RedisWorkQueueConfig} to provide configuration
 * values to Redis work queue components throughout the application.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @see io.seqera.data.workqueue.redis.RedisWorkQueueConfig
 */
@CompileStatic
@Singleton
class RedisWorkQueueConfigBean implements RedisWorkQueueConfig {

    // Each setting falls back to the legacy `wave.message-stream.*` property it replaces, so
    // a deployment configured for the message-stream implementation keeps its values after the
    // work-queue upgrade without any config change. The final defaults match the current
    // production deployment (see platform-deployment).
    @Value('${wave.work-queue.consumer-group-name:${wave.message-stream.consumer-group-name:wave-message-stream}}')
    String defaultConsumerGroupName

    @Value('${wave.work-queue.visibility-timeout:${wave.message-stream.claim-timeout:45s}}')
    Duration visibilityTimeout

    @Value('${wave.work-queue.consumer-warn-timeout:${wave.message-stream.consume-warn-timeout:45s}}')
    Duration consumerWarnTimeout

}
