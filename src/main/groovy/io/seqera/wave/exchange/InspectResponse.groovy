package io.seqera.wave.exchange

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected
import io.seqera.wave.core.spec.ContainerSpec

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
@Introspected
class InspectResponse {
    
    ContainerSpec result

}
