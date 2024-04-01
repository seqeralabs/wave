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
