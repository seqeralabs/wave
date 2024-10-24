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

import java.util.regex.Pattern
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
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

    final static private String ADDRESS_REGEX = '(rediss?://)?(?<host>[^:]+)(:(?<port>\\d+))?'

    final static private Pattern ADDRESS_PATTERN = Pattern.compile(ADDRESS_REGEX)

    @Singleton
    JedisPool createRedisPool(
            @Value('${redis.uri}') String uri,
            @Value('${redis.pool.minIdle:0}') int minIdle,
            @Value('${redis.pool.maxIdle:10}') int maxIdle,
            @Value('${redis.pool.maxTotal:50}') int maxTotal,
            @Value('${redis.client.timeout:5000}') int timeout,
            @Nullable @Value('${redis.password}') String password
    ) {
        log.info "Using redis $uri as storage for rate limit - pool minIdle: ${minIdle}; maxIdle: ${maxIdle}; maxTotal: ${maxTotal}"
        boolean ssl = uri.startsWith("rediss")
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .password(password)
                .connectionTimeoutMillis(timeout)
                .socketTimeoutMillis(timeout)
                .ssl(ssl)
                .build()

        final config = new JedisPoolConfig()
        config.setMinIdle(minIdle)
        config.setMaxIdle(maxIdle)
        config.setMaxTotal(maxTotal)

        return new JedisPool(config, buildHostAndPort(uri), clientConfig)
    }

    static HostAndPort buildHostAndPort(String address) {
        if (!address) {
            throw new IllegalArgumentException("Missing redis address")
        }
        final matcher = ADDRESS_PATTERN.matcher(address)
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Redis address: '${address}' - it should match the regex $ADDRESS_REGEX")
        }

        final host =  matcher.group('host')
        final port = matcher.group('port')
        return port
                ? new HostAndPort(host, Integer.parseInt(port))
                : new HostAndPort(host, 6379)
    }

}
