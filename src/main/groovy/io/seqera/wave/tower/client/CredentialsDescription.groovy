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

package io.seqera.wave.tower.client

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.WaveDefault

@CompileStatic
@ToString(includePackage = false, includeNames = true)
class CredentialsDescription {

    /**
     * The credential record unique ID
     */
    String id

    /**
     * The credentials provider as defined in tower. This must be {@code container-reg} for container registry
     * credentials
     */
    String provider

    /**
     * The target container registry e.g. {@code docker.io}, {@code quay.io}. Note this can also specify a full or
     * partial container repository name e.g. {@code docker.io/user/foo}
     */
    String registry

    @JsonProperty("keys")
    private void unpackRegistry(Map<String,Object> keys) {
        if (this.provider == 'container-reg') {
            this.registry = keys?.get("registry") ?: WaveDefault.DOCKER_IO
        } else {
            this.registry = null
        }
    }
}
