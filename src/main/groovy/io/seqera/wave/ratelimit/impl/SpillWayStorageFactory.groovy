/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
    LimitUsageStorage redisStorage(@NotNull RedisConfig redisConfig){
        log.info "Using redis $redisConfig.uri as storage for rate limit"
        def jedisPool = new JedisPool(redisConfig.uri)
        return RedisStorage.builder().withJedisPool(jedisPool).build()
    }
}
