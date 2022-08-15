package io.seqera.wave.exception


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpStatus
/**
 * Hold a generic http error
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class GenericException extends WaveException implements HttpError {

    final HttpStatus statusCode
    final String details

    GenericException(String message, int code, String details=null) {
        super(message)
        this.statusCode = HttpStatus.valueOf(code)
        this.details = details
    }

}
