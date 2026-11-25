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

import java.time.Instant

import io.micronaut.http.HttpStatus
import io.micronaut.http.cookie.Cookie
import io.micronaut.views.ModelAndView
import io.seqera.wave.service.access.RecordAccessService
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.scan.ContainerScanService
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

    private ViewAuthService signsIn(boolean signedIn=false) {
        final stored = signedIn
                ? new ViewSession(tokens: [(OWNER.towerEndpoint): 'tok'])
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
        given:
        def auth = signsIn(true)

        when:
        def resp = controller(Decision.REJECTED, auth).guard(OWNER, 'private', 'sess-1', RETURN_TO)

        then: 'the dead token is forgotten'
        1 * auth.forget('sess-1', _, OWNER.towerEndpoint)
        and:
        resp.status() == HttpStatus.SEE_OTHER
    }

    def 'should show the 403 page to a viewer outside the workspace' () {
        expect:
        controller(Decision.FORBIDDEN, signsIn()).guard(OWNER, 'private', 'sess-1', RETURN_TO).status() == HttpStatus.FORBIDDEN
    }

    def 'should show the 503 page when Platform cannot be reached' () {
        expect:
        controller(Decision.UNAVAILABLE, signsIn()).guard(OWNER, 'private', 'sess-1', RETURN_TO).status() == HttpStatus.SERVICE_UNAVAILABLE
    }

    def 'should let a public record through without deciding anything' () {
        given:
        def access = Mock(RecordAccessService)
        def ctl = new ViewController(accessService: access, authService: signsIn())

        when:
        def resp = ctl.guard(OWNER, 'public', 'sess-1', RETURN_TO)

        then: 'no check, and so no session read behind it'
        0 * access.check(_, _, _)
        resp == null
    }

    def 'should drop unreadable rows from a list without ever starting a sign-in' () {
        given:
        def auth = signsIn()
        def readable = controller(Decision.ALLOW, auth)
        def hidden = controller(Decision.FORBIDDEN, auth)

        when:
        def keep = readable.readDecision(OWNER, 'private', 'sess-1')
        def drop = hidden.readDecision(OWNER, 'private', 'sess-1')

        then: 'a list never redirects the viewer mid-page'
        0 * auth.startSignIn(_, _)
        keep == Decision.ALLOW
        drop == Decision.FORBIDDEN
    }

    def 'should tell a build list viewer that Platform is down, not that the records are gone' () {
        given:
        def record = new WaveBuildRecord(buildId: 'bd-abcdefghijklmn_1',
                startTime: Instant.now(), owner: OWNER, visibility: 'private')
        def ctl = new ViewController(
                accessService: Mock(RecordAccessService) { check(_, _, _) >> decision },
                authService: signsIn(true),
                buildService: Mock(ContainerBuildService) { getAllBuilds(_) >> [record] } )

        when:
        def resp = ctl.viewBuild('bd-abcdefghijklmn', 'sess-1')

        then:
        resp.status() == status
        and:
        (resp.body() as ModelAndView).view.get() == view
        ((resp.body() as ModelAndView).model.get() as Map).error_message == message

        where:
        decision             | status                         | view              | message
        Decision.UNAVAILABLE | HttpStatus.SERVICE_UNAVAILABLE | 'view-auth-error' | 'Seqera Platform could not be reached to check access. Try again shortly.'
        Decision.FORBIDDEN   | HttpStatus.OK                  | 'build-list'      | "No build visible for id 'bd-abcdefghijklmn'"
    }

    def 'should tell a scan list viewer that Platform is down, not that the records are gone' () {
        given:
        def ctl = new ViewController(
                accessService: Mock(RecordAccessService) { check(_, _, _) >> decision },
                authService: signsIn(true),
                scanService: Mock(ContainerScanService) { getAllScans(_) >>
                        [Mock(WaveScanRecord) { getOwner() >> OWNER; getVisibility() >> 'private' }] } )

        when:
        def resp = ctl.viewScan('sc-abcdefghijklmn', 'sess-1')

        then:
        resp.status() == status
        and:
        (resp.body() as ModelAndView).view.get() == view

        where:
        decision             | status                         | view
        Decision.UNAVAILABLE | HttpStatus.SERVICE_UNAVAILABLE | 'view-auth-error'
        Decision.FORBIDDEN   | HttpStatus.OK                  | 'scan-list'
    }

    /** The artifact routes the view pages link to, each serving one record type */
    private ViewController artifacts(RecordAccessService access, String visibility) {
        new ViewController(
                accessService: access,
                authService: signsIn(),
                buildService: Mock(ContainerBuildService) { getBuildRecord(_) >>
                        new WaveBuildRecord(buildId: 'bd-1_1', startTime: Instant.now(), owner: OWNER, visibility: visibility) },
                buildLogService: Mock(BuildLogService),
                mirrorService: Mock(ContainerMirrorService) { getMirrorResult(_) >>
                        Mock(MirrorResult) { getOwner() >> OWNER; getVisibility() >> visibility } },
                scanService: Mock(ContainerScanService) { getScanRecord(_) >>
                        Mock(WaveScanRecord) { getOwner() >> OWNER; getVisibility() >> visibility } } )
    }

    def 'should guard every artifact route the view pages link to' () {
        given:
        def unauthorized = Mock(RecordAccessService)
        def forbidden = Mock(RecordAccessService)
        def open = Mock(RecordAccessService)

        when: 'a private record and no session'
        def refused = CALL.call(artifacts(unauthorized, 'private'))

        then: 'the viewer is sent to sign in rather than handed the artifact'
        1 * unauthorized.check(OWNER, 'private', _) >> Decision.UNAUTHORIZED
        refused.status() == HttpStatus.SEE_OTHER

        when: 'a viewer outside the owning workspace'
        def denied = CALL.call(artifacts(forbidden, 'private'))

        then:
        1 * forbidden.check(OWNER, 'private', _) >> Decision.FORBIDDEN
        denied.status() == HttpStatus.FORBIDDEN

        when: 'the same record public'
        def served = CALL.call(artifacts(open, 'public'))

        then: 'nothing is asked of Platform and the route runs on to its artifact'
        0 * open.check(_, _, _)
        and: 'which these mocks do not hold, so it ends in a 404 rather than a refusal'
        served.status() == HttpStatus.NOT_FOUND

        where:
        ROUTE               | CALL
        'builds/logs'       | { it.viewBuildLogs('bd-1_1', null) }
        'builds/condalock'  | { it.viewCondaLock('bd-1_1', null) }
        'mirrors/logs'      | { it.viewMirrorLogs('mr-1', null) }
        'scans/spdx'        | { it.viewScanSpdx('sc-1', null) }
        'scans/logs'        | { it.viewScanLogs('sc-1', null) }
    }

}
