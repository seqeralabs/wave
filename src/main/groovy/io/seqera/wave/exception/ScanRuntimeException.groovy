package io.seqera.wave.exception

import groovy.transform.CompileStatic

/**
 * Exception thrown when the scan fails for an expected condition
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
class ScanRuntimeException extends WaveException {
    ScanRuntimeException(String message, Throwable cause) {
        super(message, cause)
    }
}
