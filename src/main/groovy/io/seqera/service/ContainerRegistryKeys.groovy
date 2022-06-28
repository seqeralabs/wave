package io.seqera.service

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ContainerRegistryKeys {
    String userName
    String password
    String registry

    static ContainerRegistryKeys fromJson(String json) {
        final root = (Map) new JsonSlurper().parseText(json)
        return new ContainerRegistryKeys(userName: root.userName, registry: root.registry)
    }
}
