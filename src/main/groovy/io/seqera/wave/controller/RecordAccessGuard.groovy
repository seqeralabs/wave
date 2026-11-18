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
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.context.ServerRequestContext
import io.seqera.wave.service.access.RecordAccessService
import io.seqera.wave.service.view.auth.ViewAuthService
import io.seqera.wave.tower.RecordOwner
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Turns an access decision into the refusal to send, for every route that serves a record.
 *
 * It is shared by the deprecated read routes and by /v1alpha3, so the two cannot drift: a record
 * is private only once a client asks for it, so guarding the old routes refuses nothing that any
 * released client can produce.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Singleton
@CompileStatic
class RecordAccessGuard {

    @Inject
    private RecordAccessService accessService

    @Inject
    @Nullable
    private ViewAuthService authService

    /**
     * Lets a caller name the Platform it believes it is talking to.
     *
     * A bearer is forwarded to whichever Platform owns the record, so any paired Platform could
     * collect a token by getting someone to read one of its private record ids. Naming the
     * endpoint is optional - no released client sends it - but when it is named and does not
     * match, the token is refused here rather than sent on.
     */
    static final String ENDPOINT_HEADER = 'X-Tower-Endpoint'

    /**
     * @return null when the caller may read the record, otherwise the refusal to send.
     *      A bearer token wins; the view session is the fallback so a signed-in browser can
     *      follow the artifact links rendered on a view page.
     */
    HttpResponse<?> refuse(RecordOwner owner, String visibility, String authorization, String sessionId) {
        // a public record admits everyone, so don't read the session store to resolve a token
        // the check would discard on its first line
        if( !RecordAccessService.isPrivate(owner, visibility) )
            return null
        final bearer = bearer(authorization)
        if( bearer && !intendedFor(owner.towerEndpoint) )
            return HttpResponse.unauthorized()
        final token = bearer ?: authService?.tokenFor(sessionId, owner?.towerEndpoint)
        final decision = accessService.check(owner, visibility, token)
        switch (decision) {
            case RecordAccessService.Decision.ALLOW:
                return null
            case RecordAccessService.Decision.REJECTED:
                // a rejected session token is no longer any use: drop it so the next page signs in again
                if( !bearer )
                    authService?.forget(sessionId, owner?.towerEndpoint)
                return HttpResponse.unauthorized()
            case RecordAccessService.Decision.UNAUTHORIZED:
                return HttpResponse.unauthorized()
            case RecordAccessService.Decision.FORBIDDEN:
                return HttpResponse.status(HttpStatus.FORBIDDEN)
            default:
                return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
        }
    }

    /** @return true unless the caller named a different Platform from the one owning the record */
    static protected boolean intendedFor(String towerEndpoint) {
        final req = ServerRequestContext.currentRequest()
        final named = req.isPresent() ? req.get().getHeaders().get(ENDPOINT_HEADER) : null
        return !named || trimmed(named) == trimmed(towerEndpoint)
    }

    static private String trimmed(String endpoint) {
        return endpoint?.trim()?.replaceAll('/+$', '')
    }

    static String bearer(String authorization) {
        if( !authorization )
            return null
        final value = authorization.trim()
        return value.toLowerCase().startsWith('bearer ') ? value.substring(7).trim() : null
    }

}
