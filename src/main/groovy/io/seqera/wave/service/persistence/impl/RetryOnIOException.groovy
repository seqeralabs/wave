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

package io.seqera.wave.service.persistence.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.retry.annotation.RetryPredicate

/**
 * Policy that retries when the exception is a {@link IOException} or was caused by a {@link IOException}
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class RetryOnIOException implements RetryPredicate {
    @Override
    boolean test(Throwable t) {
        final result = t instanceof IOException || t.cause instanceof IOException || t instanceof ReadTimeoutException
        log.debug "Checking error retry for exception [retry=$result]: $t"
        return result
    }
}
