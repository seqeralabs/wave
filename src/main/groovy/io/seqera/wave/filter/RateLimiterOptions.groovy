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
