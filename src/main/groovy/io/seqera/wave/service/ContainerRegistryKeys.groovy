package io.seqera.wave.service

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.seqera.wave.util.StringUtils

/**
 * Model the container registry keys as stored in Tower
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
        return new ContainerRegistryKeys(userName: root.userName, password: root.password, registry: root.registry)
    }

    @Override
    String toString() {
        return "ContainerRegistryKeys[registry=$registry; userName=$userName; password=${StringUtils.redact(password)})]"
    }
}
