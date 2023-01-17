package io.seqera.wave.exchange

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected

/**
 * Model the request for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Introspected
class PairServiceRequest {

    @NotBlank
    @NotNull
    String service

    @NotBlank
    @NotNull
    String endpoint

    PairServiceRequest(String service=null, String endpoint=null) {
        this.service = service
        this.endpoint = endpoint
    }
}
