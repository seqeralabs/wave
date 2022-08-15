package io.seqera.wave.exchange

import groovy.transform.CompileStatic

/**
 * Model a docker registry error response
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegistryErrorResponse {

    static class Error {
        String code
        String message
        String details
    }

    List<Error> errors = new ArrayList<>(10)

    RegistryErrorResponse(List<Error> errors) {
        this.errors = errors
    }

    RegistryErrorResponse(String message, String code=null, String details=null) {
        errors.add( new Error(code:code, message: message, details: details) )
    }

}
