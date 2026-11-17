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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpStatus
import io.seqera.wave.api.Visibility
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.GetUserInfoResponse
import io.seqera.wave.tower.client.ListWorkspacesResponse
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Decides who may read a Wave record. The Platform asked is always the one stored on the record.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class RecordAccessService {

    enum Decision { ALLOW, UNAUTHORIZED, FORBIDDEN, UNAVAILABLE }

    @Inject
    private TowerClient towerClient

    /**
     * A record is private only when it has an owner to check against. Both a null visibility -
     * written before the field existed - and an explicit "public" read as public.
     */
    static boolean isPrivate(RecordOwner owner, String visibility) {
        return owner?.towerEndpoint != null && Visibility.isPrivate(visibility)
    }

    /** Every branch is terminal: a personal-workspace record must never reach the workspace match */
    Decision check(RecordOwner owner, String visibility, String token) {
        if( !isPrivate(owner, visibility) )
            return Decision.ALLOW
        if( !token )
            return Decision.UNAUTHORIZED

        final auth = JwtAuth.of(owner.towerEndpoint, token)
        final caller = userInfo(owner.towerEndpoint, auth)
        if( caller == null )
            return Decision.UNAVAILABLE
        if( caller.user?.id == null )
            return Decision.UNAUTHORIZED

        if( owner.workspaceId == null ) {
            return caller.user.id == owner.userId ? Decision.ALLOW : Decision.FORBIDDEN
        }

        final workspaces = listWorkspaces0(owner.towerEndpoint, auth, caller.user.id)
        if( workspaces == null )
            return Decision.UNAVAILABLE
        return workspaces.hasWorkspace(owner.workspaceId) ? Decision.ALLOW : Decision.FORBIDDEN
    }

    /** @return null when the Platform could not be reached, so the caller fails closed */
    ListWorkspacesResponse listWorkspaces(PlatformId identity) {
        return listWorkspaces0(identity.towerEndpoint, JwtAuth.of(identity), identity.userId)
    }

    /** @return true when Platform rejected the token, as opposed to being unreachable */
    static private boolean rejected(Exception e) {
        return e instanceof HttpResponseException && (e as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    private GetUserInfoResponse userInfo(String endpoint, JwtAuth auth) {
        try {
            return towerClient.userInfoForAccess(endpoint, auth)
        }
        catch( Exception e ) {
            if( rejected(e) )
                throw new UnauthorizedException("Access denied by Seqera Platform at '$endpoint'")
            log.debug "Access check - user-info unavailable for '$endpoint' - cause: ${e.message}"
            return null
        }
    }

    private ListWorkspacesResponse listWorkspaces0(String endpoint, JwtAuth auth, Long userId) {
        try {
            return towerClient.listWorkspaces(endpoint, auth, userId)
        }
        catch( Exception e ) {
            if( rejected(e) )
                throw new UnauthorizedException("Access denied by Seqera Platform at '$endpoint'")
            log.debug "Access check - workspace listing unavailable for '$endpoint' - cause: ${e.message}"
            return null
        }
    }

}
