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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Produces

/**
 * Model a docker registry error response
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Produces(MediaType.APPLICATION_JSON)
class RegistryErrorResponse {

    @Canonical
    static class RegistryError {
        final String code
        final String message
    }

    List<RegistryError> errors = new ArrayList<>(10)

    /**
     * Do not remove -- required for object de-serialisation
     */
    RegistryErrorResponse() { }

    RegistryErrorResponse(List<RegistryError> errors) {
        this.errors = errors
    }

    RegistryErrorResponse(String message, String code=null) {
        errors.add( new RegistryError(code, message) )
    }

}
