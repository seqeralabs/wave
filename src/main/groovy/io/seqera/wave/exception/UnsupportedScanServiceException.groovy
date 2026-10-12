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

package io.seqera.wave.exception

import groovy.transform.CompileStatic
import io.micronaut.http.HttpStatus

/**
 * Exception thrown when the container scan service is not enabled.
 * <p>
 * This exception extends {@link HttpResponseException} and is used to indicate that
 * the requested build operation cannot proceed because the build service is disabled.
 * It responds with an HTTP status of {@link HttpStatus#NOT_IMPLEMENTED}.
 * </p>
 *
 * <p>
 * To enable the build service, ensure that the Wave configuration setting
 * {@code wave.scan.enabled} is properly configured.
 * </p>
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class UnsupportedScanServiceException extends HttpResponseException {
    UnsupportedScanServiceException() {
        super(HttpStatus.NOT_IMPLEMENTED, "Security scan service is not enabled - Check Wave configuration setting 'wave.scan.enabled'")
    }
}
