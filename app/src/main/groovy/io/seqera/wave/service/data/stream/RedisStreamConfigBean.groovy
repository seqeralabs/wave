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

package io.seqera.wave.service.data.stream

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.data.stream.impl.RedisStreamConfig
import jakarta.inject.Singleton

/**
 * Configuration bean for Redis Stream messaging in Wave application.
 * 
 * <p>This configuration provides centralized settings for Redis Stream-based
 * messaging components, including consumer group management and timeout
 * configurations. The configuration values are injected from application
 * properties with sensible defaults.
 * 
 * <p>Configuration properties:
 * <ul>
 *   <li>{@code wave.message-stream.consumer-group-name} - Name for the Redis consumer group (default: "wave-message-stream")</li>
 *   <li>{@code wave.message-stream.claim-timeout} - Timeout for claiming pending messages (default: 5s)</li>
 *   <li>{@code wave.message-stream.consume-warn-timeout} - Timeout threshold for consumer warnings (default: 4s)</li>
 * </ul>
 * 
 * <p>Example application configuration:
 * <pre>
 * # application.yml
 * wave:
 *   message-stream:
 *     consumer-group-name: "wave-prod-stream"
 *     claim-timeout: "10s"
 *     consume-warn-timeout: "8s"
 * </pre>
 * 
 * <p>This bean implements {@link RedisStreamConfig} to provide configuration
 * values to Redis Stream components throughout the application.
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @see io.seqera.data.stream.impl.RedisStreamConfig
 */
@CompileStatic
@Singleton
class RedisStreamConfigBean implements RedisStreamConfig {

    @Value('${wave.message-stream.consumer-group-name:wave-message-stream}')
    String defaultConsumerGroupName

    @Value('${wave.message-stream.claim-timeout:5s}')
    Duration claimTimeout

    @Value('${wave.message-stream.consume-warn-timeout:4s}')
    Duration consumerWarnTimeout

}
