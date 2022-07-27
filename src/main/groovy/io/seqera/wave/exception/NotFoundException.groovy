package io.seqera.wave.exception

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

/**
 * Exception mapping to HTTP Not found error (404)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@InheritConstructors
class NotFoundException extends WaveException implements HttpError {
}
