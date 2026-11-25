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

import java.util.concurrent.ExecutionException

import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.GetUserInfoResponse
import io.seqera.wave.tower.client.ListWorkspacesResponse
import io.seqera.wave.tower.client.TowerClient

import static io.seqera.wave.service.access.RecordAccessService.Decision

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RecordAccessServiceTest extends Specification {

    static final String EP = 'https://platform.io/api'

    private static RecordOwner owner(Long userId, Long workspaceId) {
        new RecordOwner(towerEndpoint: EP, userId: userId, workspaceId: workspaceId)
    }

    private static ListWorkspacesResponse listing(List<List> rows) {
        new ListWorkspacesResponse(orgsAndWorkspaces: rows.collect {
            new ListWorkspacesResponse.OrgAndWorkspace(orgId: it[0] as Long, workspaceId: it[1] as Long, roles: it[2] as Set) })
    }

    private static GetUserInfoResponse caller(Long id) {
        new GetUserInfoResponse(user: new User(id: id))
    }

    private RecordAccessService svc(GetUserInfoResponse who, ListWorkspacesResponse wsp=null) {
        def client = Mock(TowerClient)
        client.userInfoForAccess(_, _) >> { if( who==null ) throw new RuntimeException('unreachable'); return who }
        client.listWorkspaces(_, _, _) >> { if( wsp==null ) throw new RuntimeException('unreachable'); return wsp }
        return new RecordAccessService(towerClient: client)
    }

    def 'should allow a public record without a token' () {
        expect:
        svc(null).check(owner(1L, 10L), 'public', null) == Decision.ALLOW
        and: 'a record written before visibility existed is public'
        svc(null).check(owner(1L, 10L), null, null) == Decision.ALLOW
        and: 'so is one with no owner, whatever it asks for'
        svc(null).check(null, 'private', null) == Decision.ALLOW
    }

    def 'should refuse a private record without a token' () {
        expect:
        svc(null).check(owner(1L, 10L), 'private', null) == Decision.UNAUTHORIZED
    }

    def 'should admit a member of the owning workspace' () {
        expect:
        svc(caller(7L), listing([[100L, 10L, ['maintain']]])).check(owner(1L, 10L), 'private', 'tok') == Decision.ALLOW
    }

    def 'should refuse a valid user outside the workspace' () {
        expect:
        svc(caller(7L), listing([[100L, 99L, ['owner']]])).check(owner(1L, 10L), 'private', 'tok') == Decision.FORBIDDEN
    }

    def 'should admit only the same user on a personal-workspace record' () {
        expect:
        svc(caller(1L)).check(owner(1L, null), 'private', 'tok') == Decision.ALLOW
    }

    def 'should not let an org role reach another user personal-workspace record' () {
        given: 'the caller holds an org role, so their listing contains a null workspace id'
        def wsp = listing([[100L, null, ['owner']]])

        expect: 'the null branch is terminal - a null-to-null match must not admit them'
        svc(caller(7L), wsp).check(owner(1L, null), 'private', 'tok') == Decision.FORBIDDEN
    }

    def 'should fail closed when the Platform cannot be reached' () {
        expect: 'user-info unreachable'
        svc(null).check(owner(1L, 10L), 'private', 'tok') == Decision.UNAVAILABLE
        and: 'workspace listing unreachable'
        svc(caller(7L), null).check(owner(1L, 10L), 'private', 'tok') == Decision.UNAVAILABLE
    }

    def 'should refuse when Platform answers without a user' () {
        expect:
        svc(new GetUserInfoResponse(user: null)).check(owner(1L, 10L), 'private', 'tok') == Decision.UNAUTHORIZED
    }

    def 'should report a token Platform rejected apart from an unreachable Platform' () {
        given:
        def client = Mock(TowerClient) {
            userInfoForAccess(_, _) >> { throw new ExecutionException(new HttpResponseException(401, 'Unauthorized')) }
        }
        expect:
        new RecordAccessService(towerClient: client).check(owner(1L, 10L), 'private', 'stale') == Decision.REJECTED
    }

    def 'should decide the membership of the workspace a request names' () {
        given:
        def who = { Long wsp -> new PlatformId(new User(id: 7), wsp, 'tok', EP) }

        expect: 'a personal workspace is the caller own'
        svc(null).memberOf(who(null)) == Decision.ALLOW
        and:
        svc(null, listing([[100L, 10L, ['maintain']]])).memberOf(who(10L)) == Decision.ALLOW
        svc(null, listing([[100L, 99L, ['owner']]])).memberOf(who(10L)) == Decision.FORBIDDEN
        and: 'workspace listing unreachable'
        svc(null).memberOf(who(10L)) == Decision.UNAVAILABLE
    }

}
