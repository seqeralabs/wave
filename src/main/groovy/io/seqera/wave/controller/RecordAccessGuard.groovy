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
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.seqera.wave.service.access.AccessRules
import io.seqera.wave.service.access.RecordAccessService
import io.seqera.wave.service.view.auth.ViewAuthClient
import io.seqera.wave.tower.RecordOwner
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Turns an access decision into the refusal the read API sends.
 *
 * The deprecated v1alpha1 and v1alpha2 reads are left as they were: /v1 is the read API that
 * applies the check, and clients move to it rather than the old routes gaining it too.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Singleton
@CompileStatic
class RecordAccessGuard {

    @Inject
    private RecordAccessService accessService

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
     *      Bearer only: a cookie-authenticated JSON GET is readable cross-site under
     *      SameSite=Lax, so the view pages serve their artifact links from /view instead.
     */
    HttpResponse<?> refuse(RecordOwner owner, String visibility, String authorization, String namedEndpoint=null) {
        if( !AccessRules.isPrivate(owner, visibility) )
            return null
        final bearer = bearer(authorization)
        if( bearer && !intendedFor(namedEndpoint, owner.towerEndpoint) )
            return HttpResponse.unauthorized()
        final decision = accessService.check(owner, visibility, bearer)
        switch (decision) {
            case RecordAccessService.Decision.ALLOW:
                return null
            case RecordAccessService.Decision.FORBIDDEN:
                return HttpResponse.status(HttpStatus.FORBIDDEN)
            case RecordAccessService.Decision.UNAVAILABLE:
                return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            default:    // REJECTED and UNAUTHORIZED
                return HttpResponse.unauthorized()
        }
    }

    /** @return true unless the caller named a different Platform from the one owning the record */
    static protected boolean intendedFor(String named, String towerEndpoint) {
        return !named || ViewAuthClient.key(named) == ViewAuthClient.key(towerEndpoint)
    }

    static String bearer(String authorization) {
        if( !authorization )
            return null
        final value = authorization.trim()
        return value.toLowerCase().startsWith('bearer ') ? value.substring(7).trim() : null
    }

}
