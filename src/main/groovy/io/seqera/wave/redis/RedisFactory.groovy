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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.JedisClientConfig
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.exceptions.InvalidURIException
import redis.clients.jedis.util.JedisURIHelper
/**
 * Redis connection pool factory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
@Slf4j
@Requires(property = 'redis.uri')
@CompileStatic
class RedisFactory {

    @Inject
    private MeterRegistry meterRegistry

    @Singleton
    JedisPool createRedisPool(
            @Value('${redis.uri}') String connection,
            @Value('${redis.pool.minIdle:0}') int minIdle,
            @Value('${redis.pool.maxIdle:10}') int maxIdle,
            @Value('${redis.pool.maxTotal:50}') int maxTotal,
            @Value('${redis.client.timeout:5000}') int timeout,
            @Nullable @Value('${redis.password}') String password
    ) {
        log.info "Using redis ${connection} as storage for rate limit - pool minIdle: ${minIdle}; maxIdle: ${maxIdle}; maxTotal: ${maxTotal}; timeout: ${timeout}"

        final uri = URI.create(connection)
        // pool config
        final config = new JedisPoolConfig()
        config.setMinIdle(minIdle)
        config.setMaxIdle(maxIdle)
        config.setMaxTotal(maxTotal)
        // client config
        final clientConfig = clientConfig(uri, password, timeout)
        // create the jedis pool
        final result = new JedisPool(config, JedisURIHelper.getHostAndPort(uri), clientConfig)
        // Instrument the internal pool
        new JedisPoolMetricsBinder(result).bindTo(meterRegistry);
        // final return the jedis pool
        return result
    }

    protected JedisClientConfig clientConfig(URI uri, String password, int timeout) {
        if (!JedisURIHelper.isValid(uri)) {
            throw new InvalidURIException("Invalid Redis connection URI: ${uri}")
        }

        return DefaultJedisClientConfig.builder().connectionTimeoutMillis(timeout)
                .socketTimeoutMillis(timeout)
                .blockingSocketTimeoutMillis(timeout)
                .user(JedisURIHelper.getUser(uri))
                .password(password?:JedisURIHelper.getPassword(uri))
                .database(JedisURIHelper.getDBIndex(uri))
                .protocol(JedisURIHelper.getRedisProtocol(uri))
                .ssl(JedisURIHelper.isRedisSSLScheme(uri))
                .build()
    }

}
