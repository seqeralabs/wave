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
