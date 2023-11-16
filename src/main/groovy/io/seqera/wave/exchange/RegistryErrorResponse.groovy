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
        final String detail
    }

    List<RegistryError> errors = new ArrayList<>(10)

    /**
     * Do not remove -- required for object de-serialisation
     */
    RegistryErrorResponse() { }

    RegistryErrorResponse(List<RegistryError> errors) {
        this.errors = errors
    }

    RegistryErrorResponse(String code, String message, String detail=null) {
        errors.add( new RegistryError(code, message, detail) )
    }

}
