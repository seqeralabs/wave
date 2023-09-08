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

package io.seqera.wave.exchange

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected

/**
 * Model the request for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Introspected
class PairingRequest {

    @NotBlank
    @NotNull
    String service

    @NotBlank
    @NotNull
    String endpoint

    PairingRequest(String service=null, String endpoint=null) {
        this.service = service
        this.endpoint = endpoint
    }
}
