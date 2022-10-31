package io.seqera.wave.ratelimit


import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.ratelimit.impl.SpillwayRateLimiter

import jakarta.inject.Inject
import redis.clients.jedis.Jedis

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@MicronautTest(environments = ["test", "h2", "redis", "rate-limit"])
@Property(name='wave.build.timeout',value = '3s')
class SpillwayRedisRateLimiterTest extends Specification  {

    @Inject
    SpillwayRateLimiter rateLimiter

    @Inject
    ApplicationContext applicationContext

    @Value('${redis.uri}')
    String redisUrl

    Jedis jedis

    def setup() {
        jedis = new Jedis(redisUrl)
        jedis.flushAll()
    }

    def cleanup(){
        jedis.close()
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
