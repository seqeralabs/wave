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

package io.seqera.wave.service.aws

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.retry.annotation.RetryPredicate
import software.amazon.awssdk.services.sts.model.StsException

/**
 * Retry predicate for transient AWS STS errors (5xx server errors).
 * Client errors (4xx) such as AccessDenied are not retried.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
class StsRetryPredicate implements RetryPredicate {

    @Override
    boolean test(Throwable t) {
        if (t instanceof StsException) {
            final retry = t.statusCode() >= 500
            log.debug "STS error retry check [retry=$retry; code=${t.statusCode()}; error=${t.awsErrorDetails()?.errorCode()}]: ${t.message}"
            return retry
        }
        return false
    }
}