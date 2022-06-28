package io.seqera.service

import groovy.json.JsonSlurper

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerRegistryKeys {
    String userName
    String password
    String registry

    static ContainerRegistryKeys fromJson(String json) {
        Map root = new JsonSlurper().parseText(json)
        return new ContainerRegistryKeys(userName: root.userName, registry: root.registry)
    }
}
