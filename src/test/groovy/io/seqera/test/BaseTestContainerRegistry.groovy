package io.seqera.test


import io.seqera.auth.RegistryAuth
import io.seqera.auth.RegistryInfo
import org.testcontainers.containers.GenericContainer

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
trait BaseTestContainerRegistry {

    abstract GenericContainer getTestcontainers()

    String getTestRegistryUrl(String registry=null) {
        if( !registry || registry=='test' || registry=='localhost' ) {
            int port = testcontainers.firstMappedPort
            return "http://$testcontainers.containerIpAddress:$port"
        }
        else
            return registry
    }

    RegistryInfo getLocalTestRegistryInfo() {
        final uri = new URI(getTestRegistryUrl())
        new RegistryInfo('test', uri, new RegistryAuth(uri, null, RegistryAuth.Type.Basic))
    }

}
