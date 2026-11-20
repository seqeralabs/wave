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

    private static PlatformId identity(Long workspaceId, String token='tok') {
        new PlatformId(new User(id: 7L), workspaceId, token, EP)
    }

    private static Throwable rejection() {
        new ExecutionException(new HttpResponseException(401, 'Unauthorized'))
    }

    private static GetUserInfoResponse caller(Long id) {
        new GetUserInfoResponse(user: new User(id: id))
    }

    private RecordAccessService svc(GetUserInfoResponse who, ListWorkspacesResponse wsp=null) {
        def client = Mock(TowerClient)
        client.userInfoForAccess(_, _) >> { if( who==null ) throw new RuntimeException('unreachable'); return who }
        client.listWorkspacesForAccess(_, _, _) >> { if( wsp==null ) throw new RuntimeException('unreachable'); return wsp }
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

    def 'should report a rejected token as unauthorized, not unreachable' () {
        given: 'Platform answers 401, as it arrives from a future'
        def client = Mock(TowerClient)
        client.userInfoForAccess(_, _) >> { throw rejection() }
        def svc = new RecordAccessService(towerClient: client)

        expect: 'it is a rejection, not a 503 that invites a retry'
        svc.check(owner(1L, 10L), 'private', 'stale-token') == Decision.REJECTED
    }

    def 'should take the stored token on the submit path and the presented one on the read path' () {
        given: 'a caller whose access token has expired but is refreshable'
        def client = Mock(TowerClient)
        def svc = new RecordAccessService(towerClient: client)
        def identity = identity(10L, 'stale-token')

        when: 'the submit path checks membership'
        def submit = svc.memberOf(identity)

        then: 'it goes through the refreshing client, so the stored token is used'
        1 * client.listWorkspaces(EP, _, 7L) >> listing([[1L, 10L, ['owner'] as Set]])
        0 * client.listWorkspacesForAccess(_, _, _)
        submit == Decision.ALLOW

        when: 'the read path checks the same workspace'
        def read = svc.check(owner(7L, 10L), 'private', 'stale-token')

        then: 'the token is validated exactly as presented, never swapped for a stored one'
        1 * client.userInfoForAccess(EP, _) >> caller(7L)
        1 * client.listWorkspacesForAccess(EP, _, 7L) >> listing([[1L, 10L, ['owner'] as Set]])
        0 * client.listWorkspaces(_, _, _)
        read == Decision.ALLOW
    }

    def 'should decide membership without a workspace and fail closed when unreachable' () {
        given:
        def client = Mock(TowerClient)
        def svc = new RecordAccessService(towerClient: client)

        expect: 'a personal workspace needs no listing at all'
        svc.memberOf(identity(null)) == Decision.ALLOW

        when: 'Platform cannot be reached'
        def out = svc.memberOf(identity(10L))
        then:
        1 * client.listWorkspaces(_, _, _) >> { throw new RuntimeException('unreachable') }
        out == Decision.UNAVAILABLE

        when: 'Platform rejects the token'
        def bad = svc.memberOf(identity(10L))
        then:
        1 * client.listWorkspaces(_, _, _) >> { throw rejection() }
        bad == Decision.REJECTED
    }

    def 'should refuse when Platform answers without a user' () {
        expect:
        svc(new GetUserInfoResponse(user: null)).check(owner(1L, 10L), 'private', 'tok') == Decision.UNAUTHORIZED
    }

}
