package io.seqera.wave.exception

import groovy.transform.CompileStatic


/**
 *
 * Exception fired when the time to build an image is expired
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
class BuildTimeoutException extends WaveException{

    BuildTimeoutException(String message) {
        super(message)
    }
}
