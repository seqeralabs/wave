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

package io.seqera.wave.tower.client

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.WaveDefault

@CompileStatic
@ToString(includePackage = false, includeNames = true)
class CredentialsDescription {

    String id
    String provider
    String registry

    @JsonProperty("keys")
    private void unpackRegistry(Map<String,Object> keys) {
        if (this.provider == 'container-reg') {
            this.registry = keys?.get("registry")?: WaveDefault.DOCKER_IO
        } else {
            this.registry = null
        }
    }
}
