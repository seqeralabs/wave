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
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisCluster
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

    @Singleton
    @Requires(property = "redis.mode", value = "standalone")
    JedisPool createRedisPool(
            @Value('${redis.uri}') String uri,
            @Value('${redis.pool.minIdle:0}') int minIdle,
            @Value('${redis.pool.maxIdle:10}') int maxIdle,
            @Value('${redis.pool.maxTotal:50}') int maxTotal
    ) {
        log.info "Using redis $uri as storage and cache - pool minIdle: ${minIdle}; maxIdle: ${maxIdle}; maxTotal: ${maxTotal}"
        final config = new JedisPoolConfig()
        config.setMinIdle(minIdle)
        config.setMaxIdle(maxIdle)
        config.setMaxTotal(maxTotal)
        return new JedisPool(config, URI.create(uri))
    }

    @Singleton
    @Requires(property = "redis.mode", value = "cluster")
    JedisCluster createRedisCluster(
            @Value('${redis.uris}') List<String> uris,
            @Value('${redis.client.timeout:5000}') int timeout,
            @Nullable @Value('${redis.user}') String user,
            @Nullable @Value('${redis.password}') String password,
            @Nullable @Value('${redis.db}') String db,
            @Nullable @Value('${redis.ssl}') boolean ssl
    ) {
        log.info "Using redis cluster $uris as storage and cache - timeout: ${timeout}ms"

        final jedisClusterNodes = new HashSet<HostAndPort>()
        for (def uri : uris){
            if (!JedisURIHelper.isValid(URI.create(uri))) {
                throw new InvalidURIException(String.format(
                        "Cannot open Redis connection due invalid URI. %s", uri.toString()))
            }
            jedisClusterNodes.add(HostAndPort.from(uri))
        }

        def clientConfig = DefaultJedisClientConfig.builder().connectionTimeoutMillis(timeout)
                .socketTimeoutMillis(timeout)
                .blockingSocketTimeoutMillis(timeout)
        if ( user ){
            clientConfig.user(user)
        }
        if ( password ){
            clientConfig.password(password)
        }
        if ( db ){
            clientConfig.database(db as int)
        }
        if ( ssl ){
            clientConfig.ssl(true)
        }

        return new JedisCluster(jedisClusterNodes, clientConfig.build())
    }

}
