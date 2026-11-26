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
import org.apache.commons.lang3.exception.ExceptionUtils

/**
 * Generic Http response exception
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class HttpResponseException extends WaveException implements HttpError {

    final private HttpStatus statusCode

    private String response

    HttpResponseException(int statusCode, String message, String response=null) {
        super(message)
        this.statusCode = HttpStatus.valueOf(statusCode)
        this.response = response
    }

    HttpResponseException(HttpStatus statusCode, String message) {
        super(message)
        this.statusCode = statusCode
    }

    HttpResponseException(HttpStatus statusCode, String message, Throwable t) {
        super(message, t)
        this.statusCode = statusCode
    }

    HttpStatus statusCode() { statusCode }

    /**
     * @return true when this status appears anywhere in the cause chain. The call completes
     *      through a future, so the answer usually arrives wrapped in an ExecutionException.
     */
    static boolean matches(Throwable e, HttpStatus status) {
        return ExceptionUtils.getThrowableList(e).any {
            it instanceof HttpResponseException && (it as HttpResponseException).statusCode() == status
        }
    }

    @Override
    String getMessage() {
        def result = super.getMessage()
        // the upstream details are only worth reporting when there is an upstream response
        if( response )
            result += " - HTTP status=${statusCode?.code ?: '-'} - response=${response}"
        return result
    }
}
