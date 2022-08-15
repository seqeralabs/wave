package io.seqera.wave.proxy

import java.net.http.HttpRequest

import groovy.transform.CompileStatic

/**
 * Model an invalid response got by the registry client client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ClientResponseException extends Exception {

    private HttpRequest request

    HttpRequest getRequest() { request }

    ClientResponseException(String message, HttpRequest request) {
        super(message)
        this.request = request
    }

}
