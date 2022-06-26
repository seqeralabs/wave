package io.seqera.auth

import groovy.transform.Canonical

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

    RegistryInfo(String name, URI host, RegistryAuth auth) {
        this.name = name
        this.host = new URI("${host.scheme}://${host.authority}")
        this.auth = auth
    }

}
