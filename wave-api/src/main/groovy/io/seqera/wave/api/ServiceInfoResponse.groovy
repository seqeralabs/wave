package io.seqera.wave.api

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Implements Service info controller response object
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class ServiceInfoResponse {
    ServiceInfo serviceInfo

    ServiceInfoResponse() {}

    ServiceInfoResponse(ServiceInfo info) {
        this.serviceInfo = info
    }
}
