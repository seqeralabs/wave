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

    void "can acquire 1 error retry"() {
        expect:
        rateLimiter.acquireTimeoutCounter('http://foo.com')
        rateLimiter.acquireTimeoutCounter('http://bar.com')
    }

    void "should fail on multiple requests "() {
        given:
        def result = new ArrayList<Boolean>()

        when:
        result << rateLimiter.acquireTimeoutCounter('http://foo.com')
        result << rateLimiter.acquireTimeoutCounter('http://foo.com')
        result << rateLimiter.acquireTimeoutCounter('http://foo.com')
        then:
        result.count{it==false }>0
    }

}
