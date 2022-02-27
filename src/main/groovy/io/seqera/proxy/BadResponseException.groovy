package io.seqera.proxy

import java.net.http.HttpRequest

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BadResponseException extends Exception {

    private HttpRequest request

    HttpRequest getRequest() { request }

    BadResponseException(String message, HttpRequest request) {
        super(message)
        this.request = request
    }

}
