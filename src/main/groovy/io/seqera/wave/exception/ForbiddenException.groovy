package io.seqera.wave.exception

import groovy.transform.CompileStatic

/**
 * Exception mapping to HTTP Forbidden error (403)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ForbiddenException extends WaveException implements HttpError {

    ForbiddenException(String message) {
        super(message)
    }

    ForbiddenException(String message, Throwable cause) {
        super(message, cause)
    }

}
