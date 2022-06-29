package io.seqera.wave.auth

import groovy.transform.CompileStatic
/**
 * Exception throw when the registry authorization failed
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegistryUnauthorizedAccessException extends Exception {

    private String response

    RegistryUnauthorizedAccessException(String message, String response=null) {
        super(message)
        this.response = response
    }

    String getResponse() {
        return response
    }
}
