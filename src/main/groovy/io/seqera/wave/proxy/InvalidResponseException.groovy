package io.seqera.wave.proxy

import java.net.http.HttpResponse

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class InvalidResponseException extends Exception {

    HttpResponse response

    HttpResponse getResponse() { response }

    InvalidResponseException(String message, HttpResponse resp) {
        super(message)
        this.response = resp
    }
}
