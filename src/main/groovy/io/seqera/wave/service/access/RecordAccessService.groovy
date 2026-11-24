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


package io.seqera.wave.service.access

import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpStatus
import io.seqera.wave.api.Visibility
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Decides who may read a Wave record, and who may submit one into a workspace.
 * The Platform asked is always the one the record or the request names.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class RecordAccessService {

    /** The outcome of an access check, with the HTTP status a refusal is answered with */
    enum Decision {
        ALLOW(HttpStatus.OK),
        /** no token, or a token Platform knows no user for */
        UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
        /** Platform refused the token: expired or revoked */
        REJECTED(HttpStatus.UNAUTHORIZED),
        FORBIDDEN(HttpStatus.FORBIDDEN),
        /** Platform gave no answer, so the check fails closed */
        UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE)

        final HttpStatus status

        Decision(HttpStatus status) { this.status = status }
    }

    @Inject
    private TowerClient towerClient

    /**
     * A record is private only when it has an owner to check against. Both a null visibility -
     * written before the field existed - and an explicit "public" read as public.
     */
    static boolean isPrivate(RecordOwner owner, String visibility) {
        return owner?.towerEndpoint != null && Visibility.isPrivate(visibility)
    }

    /**
     * The read path: may the bearer of this token read the record? The token is validated
     * as presented, and a personal-workspace record never reaches the workspace match.
     */
    Decision check(RecordOwner owner, String visibility, String token) {
        if( !isPrivate(owner, visibility) )
            return Decision.ALLOW
        if( !token )
            return Decision.UNAUTHORIZED

        final endpoint = owner.towerEndpoint
        final auth = JwtAuth.of(endpoint, token)
        return ask(endpoint, () -> {
            final userId = towerClient.userInfoForAccess(endpoint, auth).user?.id
            if( userId == null )
                return Decision.UNAUTHORIZED
            if( owner.workspaceId == null )
                return allowIf(userId == owner.userId)
            return allowIf(towerClient.listWorkspaces(endpoint, auth, userId).hasWorkspace(owner.workspaceId))
        })
    }

    /**
     * The submit path's membership check. Wave has already authenticated this caller through the
     * refreshing path, so the stored token is used rather than the one presented.
     */
    Decision memberOf(PlatformId identity) {
        if( identity?.workspaceId == null )
            return Decision.ALLOW
        final endpoint = identity.towerEndpoint
        return ask(endpoint, () ->
                allowIf(towerClient.listWorkspaces(endpoint, JwtAuth.of(identity), identity.userId).hasWorkspace(identity.workspaceId)))
    }

    private static Decision allowIf(boolean granted) {
        return granted ? Decision.ALLOW : Decision.FORBIDDEN
    }

    /**
     * Runs a question that needs Platform, and turns a failure to get an answer into a decision:
     * REJECTED when Platform refused the token, UNAVAILABLE for anything else - an empty reply
     * included - so the check fails closed.
     */
    private static Decision ask(String endpoint, Supplier<Decision> question) {
        try {
            return question.get()
        }
        catch( Exception e ) {
            if( HttpResponseException.matches(e, HttpStatus.UNAUTHORIZED) )
                return Decision.REJECTED
            log.debug "Access check - no answer from Seqera Platform at '$endpoint' - cause: ${e.message ?: e}"
            return Decision.UNAVAILABLE
        }
    }

}
