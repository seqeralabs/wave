package io.seqera.wave.exception

import groovy.transform.CompileStatic
import io.micronaut.http.HttpStatus

/**
 * Generic Http response exception
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class HttpResponseException extends WaveException implements HttpError {

    final private HttpStatus statusCode

    HttpResponseException(int statusCode, String message) {
        super(message)
        this.statusCode = HttpStatus.valueOf(statusCode)
    }

    HttpResponseException(HttpStatus statusCode, String message) {
        super(message)
        this.statusCode = statusCode
    }

    HttpResponseException(HttpStatus statusCode, String message, Throwable t) {
        super(message, t)
        this.statusCode = statusCode
    }

    HttpStatus statusCode() { statusCode }
}
