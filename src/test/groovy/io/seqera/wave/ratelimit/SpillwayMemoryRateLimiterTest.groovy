package io.seqera.wave.ratelimit

import spock.lang.Shared
import spock.lang.Specification

import com.coveo.spillway.storage.LimitUsageStorage
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.ratelimit.impl.SpillwayRateLimiter
import jakarta.inject.Inject

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@MicronautTest(environments = ["test", "rate-limit"], rebuildContext = true)
class SpillwayMemoryRateLimiterTest extends Specification {

    @Inject
    RateLimiterConfig config

    @Inject
    SpillwayRateLimiter rateLimiter

    @Property(name = "spec", value = "can acquire 1")
    void "can acquire 1 auth resource"() {
        when:
        rateLimiter.acquireBuild(new AcquireRequest("test", null))
        then:
        noExceptionThrown()
    }

    @Property(name = "spec", value = "can acquire 1 anon")
    void "can acquire 1 anon resource"() {
        when:
        rateLimiter.acquireBuild(new AcquireRequest(null, "test"))
        then:
        noExceptionThrown()
    }

    @Property(name = "spec", value = "can acquire more")
    void "can't acquire more resources"() {
        when:
        (0..config.build.authenticated.max - 1).each {
            rateLimiter.acquireBuild(new AcquireRequest("test", null))
        }
        then:
        noExceptionThrown()

        when:
        rateLimiter.acquireBuild(new AcquireRequest("test", null))

        then:
        thrown(SlowDownException)
    }

}
