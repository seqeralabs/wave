/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

import io.micronaut.context.annotation.Replaces
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.authentication.DefaultAuthorizationExceptionHandler
import jakarta.inject.Singleton

import static io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE
import static io.micronaut.http.HttpStatus.FORBIDDEN
import static io.micronaut.http.HttpStatus.UNAUTHORIZED

/**
 * Replacement for default authorization exception handler
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Singleton
@Replaces(DefaultAuthorizationExceptionHandler)
class DefaultAuthorizationExceptionHandlerReplacement extends DefaultAuthorizationExceptionHandler {

    @Override
    protected MutableHttpResponse<?> httpResponseWithStatus(HttpRequest request,
                                                            AuthorizationException e) {
        if (e.isForbidden()) {
            return HttpResponse.status(FORBIDDEN)
        }
        HttpResponse.status(UNAUTHORIZED)
                .header(WWW_AUTHENTICATE, 'Basic realm="Wave Authentication"')
    }
}
