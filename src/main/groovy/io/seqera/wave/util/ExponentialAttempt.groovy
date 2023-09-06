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
