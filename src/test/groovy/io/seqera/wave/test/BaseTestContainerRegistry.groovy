/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.test


import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.auth.RegistryInfo
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
