package io.seqera.wave.auth

import groovy.transform.CompileStatic
import io.seqera.wave.exception.WaveException

/**
 * Exception throw when the registry authorization failed
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegistryUnauthorizedAccessException extends WaveException {

    private String response
    private Integer status

    RegistryUnauthorizedAccessException(String message, Integer status=null, String response=null) {
        super(message)
        this.status = status
        this.response = response
    }

    String getResponse() {
        return response
    }

    @Override
    String getMessage() {
        def result = super.getMessage()
        if( status!=null )
            result += " - HTTP status=$status"
        if( response )
            result += " - response=$response"
        return result
    }
}
