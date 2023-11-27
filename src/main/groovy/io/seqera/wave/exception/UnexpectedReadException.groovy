package io.seqera.wave.exception

import groovy.transform.CompileStatic

/**
 * Exception raised when an unexpected error is throw reading a binary stream
 * via {@link io.seqera.wave.util.TimedInputStream}
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class UnexpectedReadException extends IOException {
    UnexpectedReadException(String message, Throwable t) {
        super(message, t)
    }
}
