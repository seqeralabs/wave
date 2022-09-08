package io.seqera.wave.ratelimit

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.seqera.wave.configuration.RateLimiterConfiguration
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.ratelimit.impl.SpillwayRateLimiter

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class SpillwayMemoryRateLimiterTest extends Specification {


    ApplicationContext applicationContext

    SpillwayRateLimiter rateLimiter


    def setup() {
        applicationContext = ApplicationContext.run('test', 'rate-limit')
        rateLimiter = applicationContext.getBean(SpillwayRateLimiter)
    }

    void "can acquire 1 resource"() {
        when:
        rateLimiter.acquireBuild("test")
        then:
        noExceptionThrown()
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

        when:
        rateLimiter.acquireBuild("test")

        then:
        thrown(SlowDownException)
    }

}
