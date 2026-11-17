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
 * Decides who may read a Wave record, and who may submit one into a workspace.
 * The Platform asked is always the one the record or the request names.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class RecordAccessService {

    enum Decision { ALLOW, UNAUTHORIZED, REJECTED, FORBIDDEN, UNAVAILABLE }

    @Inject
    private TowerClient towerClient

    /**
     * A record is private only when it has an owner to check against. Both a null visibility -
     * written before the field existed - and an explicit "public" read as public.
     */
    static boolean isPrivate(RecordOwner owner, String visibility) {
        return owner?.towerEndpoint != null && Visibility.isPrivate(visibility)
    }

    /** A rejected token surfaces as REJECTED, so every outcome leaves by the same door */
    Decision check(RecordOwner owner, String visibility, String token) {
        try {
            return check0(owner, visibility, token)
        }
        catch( UnauthorizedException e ) {
            return Decision.REJECTED
        }
    }

    /** Every branch is terminal: a personal-workspace record must never reach the workspace match */
    private Decision check0(RecordOwner owner, String visibility, String token) {
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

        return membership(listWorkspaces0(owner.towerEndpoint, auth, caller.user.id), owner.workspaceId)
    }

    /**
     * The submit path's membership check. Wave has already authenticated this caller through the
     * refreshing path, so the stored token is used rather than the one presented.
     */
    Decision memberOf(PlatformId identity) {
        try {
            return memberOf0(identity)
        }
        catch( UnauthorizedException e ) {
            return Decision.REJECTED
        }
    }

    private Decision memberOf0(PlatformId identity) {
        if( identity?.workspaceId == null )
            return Decision.ALLOW
        final ws = guarded(identity.towerEndpoint, 'workspace listing',
                () -> towerClient.listWorkspaces(identity.towerEndpoint, JwtAuth.of(identity), identity.userId))
        return membership(ws, identity.workspaceId)
    }

    /** @return UNAVAILABLE when there is no listing to judge by, so the caller fails closed */
    private static Decision membership(ListWorkspacesResponse ws, Long workspaceId) {
        if( ws == null )
            return Decision.UNAVAILABLE
        return ws.hasWorkspace(workspaceId) ? Decision.ALLOW : Decision.FORBIDDEN
    }

    /**
     * @return true when Platform rejected the token, as opposed to being unreachable. The call
     *      completes through a future, so the rejection arrives wrapped in an ExecutionException.
     */
    static private boolean rejected(Throwable e) {
        for( Throwable t = e; t != null; t = t.cause ) {
            if( t instanceof HttpResponseException && (t as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED )
                return true
            if( t.cause === t )
                break
        }
        return false
    }

    /** @return null when Platform could not be reached; throws when it rejected the token */
    private <T> T guarded(String endpoint, String what, Supplier<T> call) {
        try {
            return call.get()
        }
        catch( Exception e ) {
            if( rejected(e) )
                throw new UnauthorizedException("Access denied by Seqera Platform at '$endpoint'")
            log.debug "Access check - $what unavailable for '$endpoint' - cause: ${e.message}"
            return null
        }
    }

    private GetUserInfoResponse userInfo(String endpoint, JwtAuth auth) {
        return guarded(endpoint, 'user-info', () -> towerClient.userInfoForAccess(endpoint, auth))
    }

    private ListWorkspacesResponse listWorkspaces0(String endpoint, JwtAuth auth, Long userId) {
        return guarded(endpoint, 'workspace listing', () -> towerClient.listWorkspacesForAccess(endpoint, auth, userId))
    }

}
