package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ExponentialAttemptTest extends Specification {

    @Unroll
    def 'should compute delay' () {
        expect:
        new ExponentialAttempt()
                .builder()
                .withBackOffBase(BACKOFF)
                .withBackOffDelay(DELAY)
                .withMaxDelay(MAX)
                .build()
                .delay(ATTEMPT) == Duration.ofMillis(EXPECTED)

        where:
        ATTEMPT | BACKOFF   | DELAY     | MAX                       | EXPECTED
        0       | 3         | 250       | Duration.ofSeconds(30)    | 250
        1       | 3         | 250       | Duration.ofSeconds(30)    | 750
        2       | 3         | 250       | Duration.ofSeconds(30)    | 2250
        3       | 3         | 250       | Duration.ofSeconds(30)    | 6750
        10      | 3         | 250       | Duration.ofSeconds(30)    | 30_000
        100     | 3         | 250       | Duration.ofSeconds(30)    | 30_000
        1000    | 3         | 250       | Duration.ofSeconds(30)    | 30_000
        10000   | 3         | 250       | Duration.ofSeconds(30)    | 30_000
    }


}
