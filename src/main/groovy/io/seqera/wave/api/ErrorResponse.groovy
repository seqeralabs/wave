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

package io.seqera.wave.api

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.core.annotation.Introspected
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Sanitized error response model that prevents exposure of internal implementation details
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@ToString(includeNames = true, includePackage = false)
@CompileStatic
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
class ErrorResponse {
    String timestamp
    Integer status
    String error
    String message
    String requestId
    String path

    ErrorResponse(String message, String error, Integer status, String path, String requestId) {
        this.timestamp = Instant.now().toString()
        this.status = status
        this.error = error
        this.message = message
        this.requestId = requestId
        this.path = path
    }
}
