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

package io.seqera.wave.ratelimit.impl

import javax.validation.constraints.NotNull

import com.coveo.spillway.storage.InMemoryStorage
import com.coveo.spillway.storage.LimitUsageStorage
import com.coveo.spillway.storage.RedisStorage
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.configuration.RedisConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton
import redis.clients.jedis.JedisPool

/**
 * A factory for different Spillway implementation
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env = 'rate-limit')
@Factory
@Slf4j
@CompileStatic
class SpillWayStorageFactory {

    @Singleton
    @Requires(missingProperty =  'redis.uri')
    LimitUsageStorage inMemoryStorage(){
        log.info "Using in memory storage for rate limit"
        return new InMemoryStorage()
    }

    @Singleton
    @Requires(property = 'redis.uri')
    LimitUsageStorage redisStorage(@NotNull RedisConfig redisConfig, JedisPool pool){
        log.info "Using redis $redisConfig.uri as storage for rate limit"
        return RedisStorage.builder().withJedisPool(pool).build()
    }
}
