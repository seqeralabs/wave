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
import io.seqera.wave.service.access.RecordAccessService
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.request.ContainerRequestService
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.view.auth.ViewAuthService
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.RecordOwner

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ReadApiControllerTest extends Specification {

    static final RecordOwner OWNER =
            new RecordOwner(towerEndpoint: 'https://platform.io/api', userId: 1L, workspaceId: 10L)

    private static RecordAccessGuard guard(RecordAccessService access, ViewAuthService auth=null) {
        new RecordAccessGuard(accessService: access, authService: auth)
    }

    private ReadApiController controller(RecordAccessService.Decision decision, WaveBuildRecord record) {
        new ReadApiController(
                guard: guard(Mock(RecordAccessService) { check(_, _, _) >> decision }),
                buildService: Mock(ContainerBuildService) { getBuildRecord(_) >> record } )
    }

    private static WaveBuildRecord build(String visibility) {
        new WaveBuildRecord(buildId: 'bd-1_1', startTime: Instant.now(), owner: OWNER, visibility: visibility)
    }

    def 'should fall back to the view session when there is no bearer token' () {
        given: 'a signed-in browser: a session cookie, no Authorization header'
        def auth = Mock(ViewAuthService)
        def seen = []
        def ctl = new ReadApiController(
                guard: guard(Mock(RecordAccessService) { check(_, _, _) >> { o, v, t -> seen << t; RecordAccessService.Decision.ALLOW } }, auth),
                buildService: Mock(ContainerBuildService) { getBuildRecord(_) >> build('private') } )

        when:
        def resp = ctl.getBuildRecord('bd-1_1', null, 'sess-1', null)

        then: 'the session token is looked up for the record owner\'s endpoint and used'
        1 * auth.tokenFor('sess-1', OWNER.towerEndpoint) >> 'cookie-tok'
        seen == ['cookie-tok']
        resp.status() == HttpStatus.OK
    }

    def 'should prefer the bearer token over the session cookie' () {
        given:
        def auth = Mock(ViewAuthService)
        def seen = []
        def ctl = new ReadApiController(
                guard: guard(Mock(RecordAccessService) { check(_, _, _) >> { o, v, t -> seen << t; RecordAccessService.Decision.ALLOW } }, auth),
                buildService: Mock(ContainerBuildService) { getBuildRecord(_) >> build('private') } )

        when:
        ctl.getBuildRecord('bd-1_1', 'Bearer header-tok', 'sess-1', null)

        then: 'the cookie is never consulted'
        0 * auth.tokenFor(_, _)
        seen == ['header-tok']
    }

    def 'should drop a rejected session token but never a rejected bearer' () {
        given:
        def auth = Mock(ViewAuthService)
        def ctl = { ->
            new ReadApiController(
                guard: guard(Mock(RecordAccessService) { check(_, _, _) >> RecordAccessService.Decision.REJECTED }, auth),
                buildService: Mock(ContainerBuildService) { getBuildRecord(_) >> build('private') } ) }

        when: 'the credential came from the cookie'
        def a = ctl().getBuildRecord('bd-1_1', null, 'sess-1', null)
        then: 'the dead session token is forgotten so the next page signs in again'
        1 * auth.forget('sess-1', OWNER.towerEndpoint)
        a.status() == HttpStatus.UNAUTHORIZED

        when: 'the credential came from the header'
        def b = ctl().getBuildRecord('bd-1_1', 'Bearer bad', 'sess-1', null)
        then: 'there is no session to drop'
        0 * auth.forget(_, _)
        b.status() == HttpStatus.UNAUTHORIZED
    }

    def 'should guard the artifact endpoints the view pages link to' () {
        given: 'a private build and a private scan'
        def denied = RecordAccessService.Decision.UNAUTHORIZED
        def scanRec = new WaveScanRecord(id: 'sc-1_1', owner: OWNER, visibility: 'private', logs: 'secret-logs')
        def ctl = { RecordAccessService.Decision d ->
            new ReadApiController(
                guard: guard(Mock(RecordAccessService) { check(_, _, _) >> d }),
                buildService: Mock(ContainerBuildService) { getBuildRecord(_) >> build('private') },
                scanService: Mock(ContainerScanService) { getScanRecord(_) >> scanRec },
                logService: Mock(BuildLogService) { fetchCondaLockStream(_) >> null } ) }

        expect: 'every artifact route refuses a private record with no credential'
        ctl(denied).getCondaLock('bd-1_1', null, null, null).status() == HttpStatus.UNAUTHORIZED
        ctl(denied).getScanLogs('sc-1_1', null, null, null).status() == HttpStatus.UNAUTHORIZED
        ctl(denied).getScanSpdx('sc-1_1', null, null, null).status() == HttpStatus.UNAUTHORIZED

        and: 'the refusal carries no payload'
        ctl(denied).getScanLogs('sc-1_1', null, null, null).body() == null

        when: 'the caller is allowed'
        def ok = ctl(RecordAccessService.Decision.ALLOW).getScanLogs('sc-1_1', null, null, null)
        then: 'the artifact is served'
        ok.status() == HttpStatus.OK
        ok.body() == 'secret-logs'
    }

    def 'should read a public record without a token' () {
        when:
        def resp = controller(RecordAccessService.Decision.ALLOW, build('public')).getBuildRecord('bd-1_1', null, null, null)

        then:
        resp.status() == HttpStatus.OK
        resp.body().buildId == 'bd-1_1'
    }

    def 'should refuse a private record without a token' () {
        expect:
        controller(RecordAccessService.Decision.UNAUTHORIZED, build('private'))
                .getBuildRecord('bd-1_1', null, null, null).status() == HttpStatus.UNAUTHORIZED
    }

    def 'should refuse a caller outside the workspace' () {
        expect:
        controller(RecordAccessService.Decision.FORBIDDEN, build('private'))
                .getBuildRecord('bd-1_1', 'Bearer tok', null, null).status() == HttpStatus.FORBIDDEN
    }

    def 'should fail closed when the owning Platform cannot be reached' () {
        expect:
        controller(RecordAccessService.Decision.UNAVAILABLE, build('private'))
                .getBuildRecord('bd-1_1', 'Bearer tok', null, null).status() == HttpStatus.SERVICE_UNAVAILABLE
    }

    def 'should not found an unknown id before checking access' () {
        expect:
        controller(RecordAccessService.Decision.FORBIDDEN, null)
                .getBuildRecord('bd-nope', null, null, null).status() == HttpStatus.NOT_FOUND
    }

    def 'should apply the same check to the status endpoint' () {
        expect:
        controller(RecordAccessService.Decision.UNAUTHORIZED, build('private'))
                .getBuildStatus('bd-1_1', null, null, null).status() == HttpStatus.UNAUTHORIZED
        and:
        controller(RecordAccessService.Decision.ALLOW, build('public'))
                .getBuildStatus('bd-1_1', null, null, null).status() == HttpStatus.OK
    }

    def 'should read the bearer token out of the header' () {
        expect:
        RecordAccessGuard.bearer(VALUE) == EXPECTED

        where:
        VALUE               | EXPECTED
        null                | null
        ''                  | null
        'Bearer abc'        | 'abc'
        'bearer abc'        | 'abc'
        '  Bearer   abc  '  | 'abc'
        'Basic abc'         | null
        'abc'               | null
    }

    def 'should hand every route the owner and visibility of the record it serves' () {
        given: 'one private record behind each of the ten routes'
        def access = Mock(RecordAccessService)
        def mirror = Mock(MirrorResult) { getOwner() >> OWNER; getVisibility() >> 'private' }
        def scan = Mock(WaveScanRecord) { getOwner() >> OWNER; getVisibility() >> 'private' }
        def container = Mock(WaveContainerRecord) { getOwner() >> OWNER; getVisibility() >> 'private' }
        def ctl = new ReadApiController(
                guard: guard(access),
                buildService: Mock(ContainerBuildService) { getBuildRecord(_) >> build('private') },
                mirrorService: Mock(ContainerMirrorService) { getMirrorResult(_) >> mirror },
                scanService: Mock(ContainerScanService) { getScanRecord(_) >> scan },
                containerService: Mock(ContainerRequestService) {
                    loadContainerRecord(_) >> container
                    getRequest(_) >> Mock(ContainerRequest) {
                        getIdentity() >> new PlatformId(new User(id: OWNER.userId), OWNER.workspaceId, 'tok', OWNER.towerEndpoint)
                        getVisibility() >> 'private' } },
                logService: Mock(BuildLogService) )

        when:
        def resp = CALL.call(ctl)

        then: 'the record being served is what gets judged, never a parent or a sibling'
        1 * access.check(OWNER, 'private', _) >> RecordAccessService.Decision.UNAUTHORIZED
        resp.status() == HttpStatus.UNAUTHORIZED
        and: 'nothing of the record leaks with the refusal'
        resp.body() == null

        where:
        ROUTE               | CALL
        'builds'            | { it.getBuildRecord('bd-1_1', null, null, null) }
        'builds/status'     | { it.getBuildStatus('bd-1_1', null, null, null) }
        'builds/logs'       | { it.getBuildLogs('bd-1_1', null, null, null) }
        'builds/condalock'  | { it.getCondaLock('bd-1_1', null, null, null) }
        'mirrors'           | { it.getMirrorRecord('mr-1', null, null, null) }
        'mirrors/logs'      | { it.getMirrorLogs('mr-1', null, null, null) }
        'scans'             | { it.getScanRecord('sc-1', null, null, null) }
        'scans/spdx'        | { it.getScanSpdx('sc-1', null, null, null) }
        'scans/logs'        | { it.getScanLogs('sc-1', null, null, null) }
        'container'         | { it.getContainerRecord('cr-1', null, null, null) }
        'container/status'  | { it.getContainerStatus('cr-1', null, null, null) }
    }

}
