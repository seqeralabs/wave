package io.seqera.wave.api

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
@CompileStatic
class ServiceInfo {

    /** Application version string */
    String version

    /** Build commit ID */
    String commitId

}
