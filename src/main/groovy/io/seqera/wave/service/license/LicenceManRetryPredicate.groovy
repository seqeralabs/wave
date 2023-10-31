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
