package io.seqera.wave.exchange

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Produces

/**
 * Model a docker registry error response
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Produces(MediaType.APPLICATION_JSON)
class RegistryErrorResponse {

    @Canonical
    static class RegistryError {
        final String code
        final String message
        final String details
    }

    List<RegistryError> errors = new ArrayList<>(10)

    /**
     * Do not remove -- required for object de-serialisation
     */
    RegistryErrorResponse() { }

    RegistryErrorResponse(List<RegistryError> errors) {
        this.errors = errors
    }

    RegistryErrorResponse(String message, String code=null, String details=null) {
        errors.add( new RegistryError(code, message, details) )
    }

}
