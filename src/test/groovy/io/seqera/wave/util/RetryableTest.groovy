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
