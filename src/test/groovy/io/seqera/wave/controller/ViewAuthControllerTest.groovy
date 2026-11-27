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

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.Cookies
import io.micronaut.views.ModelAndView
import io.seqera.wave.service.view.auth.PendingSignIn
import io.seqera.wave.service.view.auth.ViewAuthService

/**
 * How the sign-in callback ends, for each way it can fail.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ViewAuthControllerTest extends Specification {

    static final String EP = 'https://platform.io/api'

    private ViewAuthController controller(ViewAuthService auth) {
        new ViewAuthController(authService: auth)
    }

    private ViewAuthService auth(PendingSignIn pending, String session=null) {
        Mock(ViewAuthService) {
            _ * consume(_, _) >> pending
            completeSignIn(_, _, _) >> session
            stateCookie(_, _, _) >> { String st, String v, long age ->
                Cookie.of(ViewAuthService.stateCookieName(st), v).path('/view/auth').maxAge(age) }
            secureCookie() >> true
        }
    }

    private static String message(def resp) {
        return ((resp.body() as ModelAndView).model.get() as Map).error_message
    }

    /** A server-side request: the client-side HttpRequest.GET cannot carry cookies */
    private HttpRequest<?> callbackRequest(Map<String,String> cookies=[:]) {
        final jar = Mock(Cookies) {
            findCookie(_) >> { String name -> Optional.ofNullable(cookies.get(name)).map(v -> Cookie.of(name, v as String)) }
        }
        return Mock(HttpRequest) { getCookies() >> jar }
    }

    def 'should refuse a state this browser did not start' () {
        when: 'no pending sign-in matches the state and its cookie'
        def resp = controller(auth(null)).callback('a-code', 'a-state', null, null, callbackRequest())

        then:
        resp.status() == HttpStatus.UNAUTHORIZED
        message(resp) == 'This sign-in link has expired. Open the page again to retry.'
        and: 'the single-use cookie is cleared whichever way the callback ends'
        resp.getCookie(ViewAuthService.stateCookieName('a-state')).get().maxAge == 0
    }

    def 'should read and clear only the cookie its own state names' () {
        given: 'two private pages were opened before either returned'
        def first = 'aaaaaaaa11'
        def second = 'bbbbbbbb22'
        def auth = auth(new PendingSignIn(towerEndpoint: EP, codeVerifier: 'v', returnTo: '/view/builds/bd-1_1'), 'new-session')
        def request = callbackRequest([
                (ViewAuthService.stateCookieName(first)): 'hash-of-first',
                (ViewAuthService.stateCookieName(second)): 'hash-of-second' ])

        when: 'the first sign-in comes back'
        def resp = controller(auth).callback('a-code', first, null, null, request)

        then: 'its own hash is the one checked'
        1 * auth.consume(first, 'hash-of-first')
        and: 'and only its own cookie is cleared, so the second sign-in survives'
        resp.getCookie(ViewAuthService.stateCookieName(first)).get().maxAge == 0
        !resp.getCookie(ViewAuthService.stateCookieName(second)).isPresent()
    }

    def 'should name the reason when Platform did not complete the sign-in' () {
        given: 'a state this browser did start'
        def pending = new PendingSignIn(towerEndpoint: EP, codeVerifier: 'v', returnTo: '/view/builds/bd-1_1')

        when: 'the viewer refused consent at Platform'
        def resp = controller(auth(pending)).callback(null, 'a-state', 'access_denied', null, callbackRequest())

        then: 'not reported as an expired link'
        resp.status() == HttpStatus.UNAUTHORIZED
        message(resp) == 'Seqera Platform did not complete the sign-in: access_denied.'
        and: 'with a link back to the page that started it'
        ((resp.body() as ModelAndView).model.get() as Map).retry_url == '/view/builds/bd-1_1'
    }

    def 'should report a code exchange Platform refused' () {
        given:
        def pending = new PendingSignIn(towerEndpoint: EP, codeVerifier: 'v', returnTo: '/view/builds/bd-1_1')

        when: 'the exchange comes back without a token'
        def resp = controller(auth(pending, null)).callback('a-code', 'a-state', null, null, callbackRequest())

        then:
        resp.status() == HttpStatus.UNAUTHORIZED
        message(resp) == 'Seqera Platform refused the sign-in.'
    }

    def 'should send the viewer back to the page once signed in' () {
        given:
        def pending = new PendingSignIn(towerEndpoint: EP, codeVerifier: 'v', returnTo: '/view/builds/bd-1_1')

        when:
        def resp = controller(auth(pending, 'new-session')).callback('a-code', 'a-state', null, 'old-session', callbackRequest())

        then: 'see-other, so a cached redirect cannot pin the browser to a dead state'
        resp.status() == HttpStatus.SEE_OTHER
        resp.header('location') == '/view/builds/bd-1_1'
        and: 'the session cookie is host-only and not readable by scripts'
        def cookie = resp.getCookie(ViewAuthService.COOKIE).get()
        cookie.value == 'new-session'
        cookie.isHttpOnly()
        !cookie.getDomain()
    }

}
