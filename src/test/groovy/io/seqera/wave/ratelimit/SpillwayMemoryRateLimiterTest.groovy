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

package io.seqera.wave.ratelimit

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.seqera.wave.configuration.RateLimiterConfig
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

    void "can acquire 1 auth resource"() {
        when:
        rateLimiter.acquireBuild(new AcquireRequest("test", null))
        then:
        noExceptionThrown()
    }

    void "can acquire 1 anon resource"() {
        when:
        rateLimiter.acquireBuild(new AcquireRequest(null, "test"))
        then:
        noExceptionThrown()
    }

    void "can't acquire more resources"() {
        given:
        RateLimiterConfig config = applicationContext.getBean(RateLimiterConfig)

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
