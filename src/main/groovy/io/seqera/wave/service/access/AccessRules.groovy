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
import io.micronaut.http.HttpStatus
import io.seqera.wave.api.Visibility
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.service.access.RecordAccessService.Decision
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.tower.client.ListWorkspacesResponse

/**
 * The rules of record access as pure functions: no Platform call and no state.
 * {@link RecordAccessService} fetches the facts, these decide on them.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
class AccessRules {

    /**
     * A record is private only when it has an owner to check against. Both a null visibility -
     * written before the field existed - and an explicit "public" read as public.
     */
    static boolean isPrivate(RecordOwner owner, Visibility visibility) {
        return owner?.towerEndpoint != null && Visibility.isPrivate(visibility)
    }

    static Decision allowIf(boolean granted) {
        return granted ? Decision.ALLOW : Decision.FORBIDDEN
    }

    /** A personal-workspace record is readable by the user who created it and nobody else */
    static Decision ownedBy(RecordOwner owner, Long userId) {
        return allowIf(owner.userId == userId)
    }

    /** A workspace record is readable by the members of that workspace */
    static Decision memberOf(ListWorkspacesResponse workspaces, Long workspaceId) {
        return allowIf(workspaces.hasWorkspace(workspaceId))
    }

    /** Why Platform gave no answer: it refused the token, or it could not be asked at all */
    static Decision onFailure(Throwable failure) {
        return HttpResponseException.matches(failure, HttpStatus.UNAUTHORIZED) ? Decision.REJECTED : Decision.UNAVAILABLE
    }

    /** What to tell a client whose private request was not allowed into the workspace it names */
    static String refusal(Decision decision, PlatformId identity) {
        final workspace = "workspace '${identity.workspaceId}'"
        final platform = "Seqera Platform at '${identity.towerEndpoint}'"
        switch( decision ) {
            case Decision.FORBIDDEN:
                return "Private container request denied - your user is not a member of $workspace on $platform - check the workspace ID or omit the `visibility` attribute"
            case Decision.REJECTED:
                return "Private container request denied - $platform rejected the access token, it may be expired or revoked - provide a valid access token"
            case Decision.UNAVAILABLE:
                return "Unable to verify your access to $workspace because $platform is not reachable - try again later"
            default:
                throw new IllegalArgumentException("Not a workspace access refusal: $decision")
        }
    }

}
