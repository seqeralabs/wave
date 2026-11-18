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

import spock.lang.Specification

import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.seqera.wave.service.access.RecordAccessService
import io.seqera.wave.service.view.auth.SignInRedirect
import io.seqera.wave.service.view.auth.ViewAuthService
import io.seqera.wave.service.view.auth.ViewSession
import io.seqera.wave.tower.RecordOwner

import static io.seqera.wave.service.access.RecordAccessService.Decision

/**
 * What a view page does with each access decision.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ViewGuardTest extends Specification {

    static final RecordOwner OWNER =
            new RecordOwner(towerEndpoint: 'https://platform.io/api', userId: 1L, workspaceId: 10L)

    static final String RETURN_TO = '/view/builds/bd-1_1'

    private ViewController controller(Decision decision, ViewAuthService auth) {
        new ViewController(
                accessService: Mock(RecordAccessService) { check(_, _, _) >> decision },
                authService: auth )
    }

    private ViewAuthService signsIn(Long issuedAt=null) {
        final stored = issuedAt != null
                ? new ViewSession(tokens: [(OWNER.towerEndpoint): 'tok'], issuedAt: [(OWNER.towerEndpoint): issuedAt])
                : null
        Mock(ViewAuthService) {
            startSignIn(_, _) >> new SignInRedirect('https://platform.io/authorize?state=abc', 'hashed')
            session(_) >> stored
            secureCookie() >> true
            stateCookie(_, _) >> { String v, long age -> Cookie.of(ViewAuthService.STATE_COOKIE, v).path('/view/auth').httpOnly(true).maxAge(age) }
        }
    }

    def 'should send an unauthenticated viewer to sign in, carrying the state cookie' () {
        when:
        def resp = controller(Decision.UNAUTHORIZED, signsIn()).guard(OWNER, 'private', null, RETURN_TO)

        then:
        resp.status() == HttpStatus.SEE_OTHER
        resp.header('location') == 'https://platform.io/authorize?state=abc'
        and: 'the reply is only accepted back from this browser'
        resp.getCookie(ViewAuthService.STATE_COOKIE).get().value == 'hashed'
        resp.getCookie(ViewAuthService.STATE_COOKIE).get().isHttpOnly()
    }

    def 'should show the 401 page when that Platform cannot sign viewers in' () {
        given: 'no client registered, so startSignIn has no url to offer'
        def auth = Mock(ViewAuthService) { startSignIn(_, _) >> null; session(_) >> null }

        when:
        def resp = controller(Decision.UNAUTHORIZED, auth).guard(OWNER, 'private', null, RETURN_TO)

        then:
        resp.status() == HttpStatus.UNAUTHORIZED
        and: 'and does not offer a sign-in that cannot happen'
        !resp.getCookie(ViewAuthService.STATE_COOKIE).isPresent()
    }

    def 'should drop a rejected session token and send the viewer to sign in again' () {
        given: 'a token old enough that it has simply expired'
        def auth = signsIn(System.currentTimeMillis() - 3_600_000)

        when:
        def resp = controller(Decision.REJECTED, auth).guard(OWNER, 'private', 'sess-1', RETURN_TO)

        then: 'the dead token is forgotten'
        1 * auth.forget('sess-1', OWNER.towerEndpoint)
        and:
        resp.status() == HttpStatus.SEE_OTHER
    }

    def 'should not loop when Platform refuses the token it has just issued' () {
        given: 'the token arrived seconds ago, so Platform would hand back another without prompting'
        def auth = signsIn(System.currentTimeMillis() - 2_000)

        when:
        def resp = controller(Decision.REJECTED, auth).guard(OWNER, 'private', 'sess-1', RETURN_TO)

        then: 'the page says so instead of bouncing the browser round again'
        resp.status() == HttpStatus.UNAUTHORIZED
        0 * auth.startSignIn(_, _)
    }

    def 'should show the 403 page to a viewer outside the workspace' () {
        expect:
        controller(Decision.FORBIDDEN, signsIn()).guard(OWNER, 'private', 'sess-1', RETURN_TO).status() == HttpStatus.FORBIDDEN
    }

    def 'should show the 503 page when Platform cannot be reached' () {
        expect:
        controller(Decision.UNAVAILABLE, signsIn()).guard(OWNER, 'private', 'sess-1', RETURN_TO).status() == HttpStatus.SERVICE_UNAVAILABLE
    }

    def 'should let a public record through untouched' () {
        expect:
        controller(Decision.ALLOW, signsIn()).guard(OWNER, 'public', null, RETURN_TO) == null
    }

    def 'should drop unreadable rows from a list without ever starting a sign-in' () {
        given:
        def auth = signsIn()
        def readable = controller(Decision.ALLOW, auth)
        def hidden = controller(Decision.FORBIDDEN, auth)

        when:
        def keep = readable.isReadable(OWNER, 'private', 'sess-1')
        def drop = hidden.isReadable(OWNER, 'private', 'sess-1')

        then: 'a list never redirects the viewer mid-page'
        0 * auth.startSignIn(_, _)
        keep
        !drop
    }

}
