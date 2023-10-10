/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder

/**
 * Implements helper class to compute an exponential delay
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Builder(prefix = 'with')
@CompileStatic
class ExponentialAttempt {

    int backOffBase = 2
    int backOffDelay = 250
    Duration maxDelay = Duration.ofMinutes(1)
    int maxAttempts = Integer.MAX_VALUE
    int attempt

    Duration next() {
        delay(++attempt)
    }

    Duration delay() {
        delay(attempt)
    }

    Duration delay(int attempt) {
        final result = Math.min((Math.pow(backOffBase, attempt) as long) * backOffDelay, maxDelay.toMillis())
        return result>0 ? Duration.ofMillis(result) : maxDelay
    }

    int current() { attempt }

    boolean canAttempt() {
        attempt <= maxAttempts
    }

    void reset() {
        attempt=0
    }
}
