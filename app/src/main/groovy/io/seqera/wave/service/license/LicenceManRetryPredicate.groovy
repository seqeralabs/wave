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

package io.seqera.wave.service.license

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.retry.annotation.RetryPredicate
/**
 * Implements a retry logic for License manager requests
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class LicenceManRetryPredicate implements RetryPredicate {

    boolean test(Throwable t) {
        final result = \
                // retry on all IOException
                t instanceof IOException || \
                t.cause instanceof IOException || \
                // retry on read timeout exceptions
                t instanceof ReadTimeoutException || \
                // retry on all http error codes (note: 404 'NOT FOUND' does not throw any exception)
                t instanceof HttpClientResponseException
        log.info "Checking License manager error retry for exception [retry=$result]: $t"
        return result
    }

}
