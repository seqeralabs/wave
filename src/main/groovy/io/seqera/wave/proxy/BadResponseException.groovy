package io.seqera.wave.proxy

import java.net.http.HttpRequest

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class BadResponseException extends Exception {

    private HttpRequest request

    HttpRequest getRequest() { request }

    BadResponseException(String message, HttpRequest request) {
        super(message)
        this.request = request
    }

}
