package io.seqera.wave.exception

/**
 * Base class for Wave exceptions
 */
class BadRequestException extends WaveException implements HttpError {

    BadRequestException() {}
    BadRequestException(String message) { super(message) }
    BadRequestException(String message, Throwable cause) { super(message, cause) }

}
