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

package io.seqera.wave.ratelimit

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.ratelimit.impl.SpillwayRateLimiter
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.Jedis

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class SpillwayRedisRateLimiterTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    @Shared
    SpillwayRateLimiter rateLimiter

    @Shared
    Jedis jedis

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST   : redisHostName,
                REDIS_PORT   : redisPort
        ], 'test', 'redis','rate-limit')
        rateLimiter = applicationContext.getBean(SpillwayRateLimiter)
        jedis = new Jedis(redisHostName, redisPort as int)
        jedis.flushAll()
    }

    def cleanup(){
        jedis.close()
        applicationContext.close()
    }

    void "can acquire 1 auth resource"() {
        when:
        rateLimiter.acquireBuild(new AcquireRequest("test", null))
        then:
        noExceptionThrown()
        and:
        jedis.scan("0").result.size() == 1
        jedis.scan("0").result.first().startsWith('spillway|authenticatedBuilds|perUser|test')
    }

    void "can acquire 1 anon resource"() {
        when:
        rateLimiter.acquireBuild(new AcquireRequest(null, "test"))
        then:
        noExceptionThrown()
        and:
        jedis.scan("0").result.size() == 1
        jedis.scan("0").result.first().startsWith('spillway|anonymousBuilds|perUser|test')
    }

    void "can't acquire more resources"() {
        given:
        RateLimiterConfig config = applicationContext.getBean(RateLimiterConfig)

        when:
        (0..config.build.authenticated.max - 1).each {
            rateLimiter.acquireBuild( new AcquireRequest("test", null))
        }
        then:
        noExceptionThrown()
        and:
        jedis.scan("0").result.size() == 1

        when:
        rateLimiter.acquireBuild( new AcquireRequest("test", null))

        then:
        thrown(SlowDownException)
    }

}
