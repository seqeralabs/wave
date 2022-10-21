package io.seqera.wave.auth

import groovy.transform.Canonical
import io.seqera.wave.WaveDefault

/**
 * Model a container registry server and authorization info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class RegistryInfo {

    final String name
    final URI host
    final RegistryAuth auth
    final String indexHost

    RegistryInfo(String name, URI endpoint, RegistryAuth auth) {
        this.name = name
        this.host = new URI("${endpoint.scheme}://${endpoint.authority}")
        this.auth = auth
        this.indexHost = indexHostname0(endpoint)
    }

    protected String indexHostname0(URI uri) {
        def result = "$uri.scheme://$uri.host"
        // this is required by Kaniko bug
        // https://github.com/GoogleContainerTools/kaniko/issues/1209
        if( result == WaveDefault.DOCKER_REGISTRY_1 )
            result = WaveDefault.DOCKER_INDEX_V1
        return result
    }

}
