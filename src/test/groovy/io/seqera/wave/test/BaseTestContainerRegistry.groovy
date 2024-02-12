/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
