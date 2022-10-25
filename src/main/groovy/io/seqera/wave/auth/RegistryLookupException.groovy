package io.seqera.wave.auth

import groovy.transform.CompileStatic
import io.seqera.wave.exception.WaveException
/**
 * Generic registry authorization exception
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegistryLookupException extends WaveException {
    RegistryLookupException(String message) {
        super(message)
    }

    RegistryLookupException(String message, Throwable t) {
        super(message, t)
    }
}
