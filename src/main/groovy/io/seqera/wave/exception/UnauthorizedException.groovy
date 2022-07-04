package io.seqera.wave.exception

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

/**
 * Exception mapping to HTTP Unauthorized error (401)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@InheritConstructors
class UnauthorizedException extends WaveException implements HttpError {
}
