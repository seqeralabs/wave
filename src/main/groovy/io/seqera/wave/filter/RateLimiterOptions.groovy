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

package io.seqera.wave.filter


import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.ConfigurationProperties
/**
 * Rate limiter config options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@ConfigurationProperties("rate-limit.httpRequest")
class RateLimiterOptions {

    Duration timeoutDuration

    Duration limitRefreshPeriod

    Integer limitForPeriod

    int statusCode

    void validate() {
        assert limitForPeriod>0, "Rate-limiter limitForPeriod must be greater than zero"
    }
}
