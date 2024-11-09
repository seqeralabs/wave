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

package io.seqera.wave.exchange

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Produces
import io.seqera.wave.encoder.MoshiEncodeStrategy

/**
 * Model a docker registry error response
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Produces(MediaType.APPLICATION_JSON)
@ToString(includePackage = false, includeNames = true)
class RegistryErrorResponse {

    @Canonical
    static class RegistryError {
        /**
         * The error code as defined in the registry API. see
         * https://distribution.github.io/distribution/spec/api/#errors-2
         */
        final String code

        /**
         * The error message
         */
        final String message
    }

    final List<RegistryError> errors = new ArrayList<>(10)

    /**
     * Do not remove -- required for object de-serialisation
     */
    protected RegistryErrorResponse() { }

    /**
     * Creates a {@link RegistryErrorResponse} object with the specified error
     * code and message.
     *
     * @param code  The error code as string.
     * @param message The error message as string
     */
    RegistryErrorResponse(String code, String message) {
        errors.add( new RegistryError(code, message) )
    }

    /**
     * Parse a JSON error response into a {@link RegistryErrorResponse}.
     *
     * @param json
     *      The JSON error response as a string
     * @return
     *      The corresponding {@link RegistryErrorResponse} object
     */
    static RegistryErrorResponse parse(String json) throws IllegalArgumentException {
        try {
            final decoder = new MoshiEncodeStrategy<RegistryErrorResponse>() {}
            return decoder.decode(json)
        }
        catch (Throwable t) {
            throw new IllegalArgumentException("Unable to parse registry error response - offending value: $json", t)
        }
    }
}
