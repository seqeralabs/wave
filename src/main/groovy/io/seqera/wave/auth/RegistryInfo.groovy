/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
