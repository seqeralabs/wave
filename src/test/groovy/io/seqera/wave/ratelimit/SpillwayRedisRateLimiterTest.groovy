package io.seqera.wave.ratelimit


import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.seqera.wave.exception.RateLimitException
import io.seqera.wave.configuration.RateLimiterConfiguration
import io.seqera.wave.ratelimit.impl.SpillwayRateLimiter
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.JedisPool

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class SpillwayRedisRateLimiterTest extends Specification implements RedisTestContainer {


    ApplicationContext applicationContext

    SpillwayRateLimiter rateLimiter

    JedisPool jedisPool

    def setup() {
        restartRedis()
        applicationContext = ApplicationContext.run([
                REDIS_HOST   : redisHostName,
                REDIS_PORT   : redisPort
        ], 'test', 'redis','rate-limit')
        rateLimiter = applicationContext.getBean(SpillwayRateLimiter)
        jedisPool = new JedisPool(redisHostName, redisPort as int)
    }

    void "can acquire 1 resource"() {
        when:
        rateLimiter.acquireBuild("test")
        then:
        noExceptionThrown()
        and:
        jedisPool.resource.scan("0").result.size() == 1
        jedisPool.resource.scan("0").result.first().startsWith('spillway|builds|builds|test|')
    }

    void "can't acquire more resources"() {
        given:
        RateLimiterConfiguration config = applicationContext.getBean(RateLimiterConfiguration)

        when:
        (0..config.build.max - 1).each {
            rateLimiter.acquireBuild("test")
        }
        then:
        noExceptionThrown()
        and:
        jedisPool.resource.scan("0").result.size() == 1

        when:
        rateLimiter.acquireBuild("test")

        then:
        thrown(RateLimitException)
    }

}
