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
class DockerRegistryException extends WaveException implements HttpError {

    final HttpStatus statusCode
    final String error

    DockerRegistryException(String message, int code, String error) {
        super(message)
        this.statusCode = HttpStatus.valueOf(code)
        this.error = error
    }

}
