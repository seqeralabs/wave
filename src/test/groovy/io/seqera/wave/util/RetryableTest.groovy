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

package io.seqera.wave.util

import spock.lang.Specification

import java.time.Duration

import dev.failsafe.FailsafeException
import groovy.util.logging.Slf4j

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class RetryableTest extends Specification {

    def 'should retry execution'  () {
        given:
        def config = Mock(Retryable.Config){
            getDelay() >> Duration.ofSeconds(1)
            getMaxDelay() >> Duration.ofSeconds(10)
            getMaxAttempts() >> 10
            getJitter() >> 0.25
        }
        and:
        int attempts = 0
        def retryable = Retryable.of(config).onRetry { log.info("Attempt ${it.attemptCount}") }
        when:
        def result = retryable.apply {
            if( attempts++<2) throw new IOException("Oops failed!")
            return attempts
        }
        then:
        result == 3
    }

    def 'should throw an exception'  () {
        given:
        def config = Mock(Retryable.Config){
            getDelay() >> Duration.ofSeconds(1)
            getMaxDelay() >> Duration.ofSeconds(10)
            getMaxAttempts() >> 1
            getJitter() >> 0.25
        }
        and:
        def retryable = Retryable.of(config).onRetry { log.info("Attempt ${it.attemptCount}") }
        when:
        retryable.apply(()-> {throw new IOException("Oops failed!")})
        then:
        def e = thrown(FailsafeException)
        e.cause instanceof IOException
    }

}
