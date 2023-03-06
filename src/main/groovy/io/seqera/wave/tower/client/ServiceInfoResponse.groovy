package io.seqera.wave.tower.client

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model Tower service info response
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ServiceInfoResponse {

    @ToString(includePackage = false, includeNames = true)
    static class ServiceInfo {
        String version
        String apiVersion
        String commitId
        Boolean waveEnabled
    }

    ServiceInfo serviceInfo
}
