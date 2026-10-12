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

package io.seqera.wave.redis

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.activator.redis.RedisActivator
import jakarta.inject.Singleton

/**
 * Redis activation strategy for Wave application components.
 * 
 * <p>This implementation provides conditional activation of Redis-dependent
 * components by requiring both the 'redis' environment and a configured
 * Redis URI. This dual-condition approach ensures Redis components are only
 * activated when:
 * <ul>
 *   <li>The application is explicitly configured to use Redis (via environment)</li>
 *   <li>A Redis connection URI is properly configured</li>
 * </ul>
 * 
 * <p>Example configuration that would activate this strategy:
 * <pre>
 * # application.yml
 * micronaut:
 *   environments: redis
 * 
 * redis:
 *   uri: redis://localhost:6379
 * </pre>
 * 
 * <p>Components that depend on Redis availability should require this bean:
 * <pre>
 * &#64;Singleton
 * &#64;Requires(beans = RedisActivator.class)
 * class RedisMessageProcessor {
 *     // Redis-dependent implementation
 * }
 * </pre>
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @see io.seqera.activator.redis.RedisActivator
 */
@Requires(env = 'redis')
@Requires(property = 'redis.uri')
@CompileStatic
@Singleton
class RedisActivationStrategy implements RedisActivator {
}
