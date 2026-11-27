/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.service.view.auth.ViewAuthService
import jakarta.inject.Inject

/**
 * Receives the browser back from a Platform sign-in.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Controller("/view/auth")
@ExecuteOn(TaskExecutors.BLOCKING)
@CompileStatic
class ViewAuthController {

    @Inject
    private ViewAuthService authService

    @Get('/callback')
    @Produces(MediaType.TEXT_HTML)
    HttpResponse<?> callback(@Nullable @QueryValue String code,
                             @Nullable @QueryValue String state,
                             @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId)
    {
        // an unknown or expired state means the sign-in cannot be tied to this browser
        final pending = authService.consume(state)
        if( !pending || !code )
            return unauthorized('This sign-in link has expired. Open the page again to retry.')

        final newSession = authService.completeSignIn(pending, code, sessionId)
        if( !newSession )
            return unauthorized('Seqera Platform refused the sign-in.')

        // see-other, not the default permanent redirect: a cached one would pin the browser
        // to a dead single-use state
        return HttpResponse
                .seeOther(URI.create(pending.returnTo ?: '/'))
                .cookie(sessionCookie(newSession))
    }

    protected Cookie sessionCookie(String sessionId) {
        // host-only: no domain is set, so no sibling subdomain receives it
        return Cookie.of(ViewAuthService.COOKIE, sessionId)
                .path('/')
                .httpOnly(true)
                .secure(authService.secureCookie())
                .sameSite(SameSite.Lax)
    }

    static protected HttpResponse<?> unauthorized(String message) {
        return ViewController.authError(HttpStatus.UNAUTHORIZED, message)
    }

}
