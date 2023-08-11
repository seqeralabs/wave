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
