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
