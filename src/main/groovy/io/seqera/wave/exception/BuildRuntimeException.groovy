package io.seqera.wave.exception

import groovy.transform.CompileStatic

/**
 * Exception thrown when the build fails for an expected condition
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class BuildRuntimeException extends WaveException {

    BuildRuntimeException(String message, Throwable cause) {
        super(message, cause)
    }

}
