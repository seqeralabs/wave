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

import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ExecutionException

import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.ListWorkspacesResponse

import static io.seqera.wave.service.access.RecordAccessService.Decision

class AccessRulesTest extends Specification {

    static final String EP = 'https://platform.io/api'

    @Unroll
    def 'should read a record as private=#EXPECTED when owner=#OWNER and visibility=#VISIBILITY' () {
        expect:
        AccessRules.isPrivate(OWNER, VISIBILITY) == EXPECTED

        where:
        OWNER                                          | VISIBILITY | EXPECTED
        new RecordOwner(towerEndpoint: EP, userId: 1L) | 'private'  | true
        new RecordOwner(towerEndpoint: EP, userId: 1L) | 'public'   | false
        new RecordOwner(towerEndpoint: EP, userId: 1L) | null       | false
        null                                           | 'private'  | false
    }

    def 'should let only its owner read a personal-workspace record' () {
        given:
        def owner = new RecordOwner(towerEndpoint: EP, userId: 1L)
        expect:
        AccessRules.ownedBy(owner, 1L) == Decision.ALLOW
        AccessRules.ownedBy(owner, 7L) == Decision.FORBIDDEN
    }

    def 'should let the members of a workspace read its records' () {
        given:
        def workspaces = new ListWorkspacesResponse(orgsAndWorkspaces: [
                new ListWorkspacesResponse.OrgAndWorkspace(orgId: 100L, workspaceId: 10L) ])
        expect:
        AccessRules.memberOf(workspaces, 10L) == Decision.ALLOW
        AccessRules.memberOf(workspaces, 99L) == Decision.FORBIDDEN
    }

    def 'should tell a rejected token from an unreachable Platform' () {
        expect:
        AccessRules.onFailure(new ExecutionException(new HttpResponseException(401, 'Unauthorized'))) == Decision.REJECTED
        AccessRules.onFailure(new RuntimeException('unreachable')) == Decision.UNAVAILABLE
    }

    def 'should explain a refusal with the workspace and the Platform it concerns' () {
        given:
        def identity = new PlatformId(new User(id: 1), 999L, 'tok', EP)
        expect:
        AccessRules.refusal(Decision.FORBIDDEN, identity) == "Private container request denied - your user is not a member of workspace '999' on Seqera Platform at '$EP' - check the workspace ID or omit the `visibility` attribute"
        AccessRules.refusal(Decision.REJECTED, identity) == "Private container request denied - Seqera Platform at '$EP' rejected the access token, it may be expired or revoked - provide a valid access token"
        AccessRules.refusal(Decision.UNAVAILABLE, identity) == "Unable to verify your access to workspace '999' because Seqera Platform at '$EP' is not reachable - try again later"
    }

    def 'should not explain a decision that is not a workspace access refusal' () {
        when:
        AccessRules.refusal(DECISION, new PlatformId(new User(id: 1), 999L, 'tok', EP))
        then:
        thrown(IllegalArgumentException)

        where:
        DECISION << [Decision.ALLOW, Decision.UNAUTHORIZED]
    }

}
