package io.seqera.wave.exception


/**
 *
 * Exception fired when the time to build an image is expired
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class BuildTimeoutException extends WaveException{

    BuildTimeoutException(String message) {
        super(message)
    }
}
