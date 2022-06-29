package io.seqera.wave.proxy

import java.net.http.HttpResponse

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class InvalidResponseException extends Exception {

    HttpResponse response

    HttpResponse getResponse() { response }

    InvalidResponseException(String message, HttpResponse resp) {
        super(message)
        this.response = resp
    }
}
