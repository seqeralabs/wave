package io.seqera.wave.controller

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.http.hateoas.JsonError


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class JsonDockerError extends JsonError{

    private List<Map<String, String>> errors;

    /**
     * @param message The message
     */
    JsonDockerError(String code, String message) {
        super(message)
        errors.add( [code:code, message:message])
    }

    @JsonProperty("errors")
    List<Map<String, String>> getErrors() {
        return errors
    }
}
