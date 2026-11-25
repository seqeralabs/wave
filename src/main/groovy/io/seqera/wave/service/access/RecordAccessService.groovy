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
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Decides who may read a Wave record, and who may submit one into a workspace.
 * It asks Platform for the facts and leaves the deciding to {@link AccessRules}.
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

    /** The read path: may the bearer of this token read the record? */
    Decision check(RecordOwner owner, String visibility, String token) {
        if( !AccessRules.isPrivate(owner, visibility) )
            return Decision.ALLOW
        if( !token )
            return Decision.UNAUTHORIZED
        final auth = JwtAuth.of(owner.towerEndpoint, token)
        return askPlatform(owner.towerEndpoint, () -> reader(owner, auth))
    }

    /** The submit path: does the caller belong to the workspace the request names? */
    Decision memberOf(PlatformId identity) {
        // a personal workspace is the caller's own
        if( identity?.workspaceId == null )
            return Decision.ALLOW
        final auth = JwtAuth.of(identity)
        return askPlatform(identity.towerEndpoint, () -> member(identity.towerEndpoint, auth, identity.userId, identity.workspaceId))
    }

    /**
     * The organisation owning the workspace the request names, for the record's owner.
     *
     * Reads the same listing the membership check just fetched, which is cached for the access
     * check's ttl, so it costs no further Platform call.
     *
     * @return null when the request names no workspace, or the listing does not say
     */
    Long orgIdOf(PlatformId identity) {
        if( identity?.workspaceId == null )
            return null
        try {
            final ws = towerClient.listWorkspaces(identity.towerEndpoint, JwtAuth.of(identity), identity.userId)
            return ws?.orgsAndWorkspaces?.find { it.workspaceId == identity.workspaceId }?.orgId
        }
        catch( Exception e ) {
            // membership has already been granted on this listing, and the org id is recorded for
            // accounting only, so failing to read it must not fail the request
            log.debug "Access check - org id unavailable for '$identity.towerEndpoint' - cause: ${e.message}"
            return null
        }
    }

    /** The token must belong to a Platform user; the kind of workspace then says which rule applies */
    private Decision reader(RecordOwner owner, JwtAuth auth) {
        final userId = towerClient.userInfoForAccess(owner.towerEndpoint, auth).user?.id
        if( userId == null )
            return Decision.UNAUTHORIZED
        if( owner.workspaceId == null )
            return AccessRules.ownedBy(owner, userId)
        // the read path judges the token as presented, so it takes no newer one from the store
        final ws = towerClient.listWorkspacesForAccess(owner.towerEndpoint, auth, userId)
        return AccessRules.memberOf(ws, owner.workspaceId)
    }

    private Decision member(String endpoint, JwtAuth auth, Long userId, Long workspaceId) {
        return AccessRules.memberOf(towerClient.listWorkspaces(endpoint, auth, userId), workspaceId)
    }

    /** Any failure to get an answer from Platform - an empty reply included - becomes a refusal */
    private static Decision askPlatform(String endpoint, Supplier<Decision> question) {
        try {
            return question.get()
        }
        catch( Exception e ) {
            log.debug "Access check - no answer from Seqera Platform at '$endpoint' - cause: ${e.message ?: e}"
            return AccessRules.onFailure(e)
        }
    }

}
