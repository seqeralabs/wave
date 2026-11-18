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
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequestService
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.RecordOwner

/**
 * The deprecated read routes apply the same check as /v1alpha3.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class LegacyReadApiGuardTest extends Specification {

    static final RecordOwner OWNER =
            new RecordOwner(towerEndpoint: 'https://platform.io/api', userId: 1L, workspaceId: 10L)

    private RecordAccessService access

    private RecordAccessGuard guard(RecordAccessService svc) {
        new RecordAccessGuard(accessService: svc)
    }

    private BuildController builds(RecordAccessService svc) {
        new BuildController(
                guard: guard(svc),
                buildService: Mock(ContainerBuildService) {
                    getBuildRecord(_) >> new WaveBuildRecord(buildId: 'bd-1_1', startTime: Instant.now(),
                            owner: OWNER, visibility: 'private') },
                logService: Mock(BuildLogService) )
    }

    private ScanController scans(RecordAccessService svc) {
        new ScanController(
                guard: guard(svc),
                scanService: Mock(ContainerScanService) {
                    getScanRecord(_) >> Mock(WaveScanRecord) { getOwner() >> OWNER; getVisibility() >> 'private' } } )
    }

    private MirrorController mirrors(RecordAccessService svc) {
        new MirrorController(
                guard: guard(svc),
                mirrorService: Mock(ContainerMirrorService) {
                    getMirrorResult(_) >> Mock(MirrorResult) { getOwner() >> OWNER; getVisibility() >> 'private' } } )
    }

    private ContainerController containers(RecordAccessService svc) {
        new ContainerController(
                guard: guard(svc),
                containerService: Mock(ContainerRequestService) {
                    loadContainerRecord(_) >> Mock(WaveContainerRecord) { getOwner() >> OWNER; getVisibility() >> 'private' }
                    // the status route guards from the request entry, which the status path reads anyway
                    getRequest(_) >> Mock(ContainerRequest) {
                        getIdentity() >> new PlatformId(new User(id: OWNER.userId), OWNER.workspaceId, 'tok', OWNER.towerEndpoint)
                        getVisibility() >> 'private' } } )
    }

    def 'should refuse a private record on every deprecated read route' () {
        given:
        access = Mock(RecordAccessService)

        when:
        def resp = CALL.call(this)

        then: 'the route judges the record it is about to serve'
        1 * access.check(OWNER, 'private', _) >> RecordAccessService.Decision.UNAUTHORIZED
        resp.status() == HttpStatus.UNAUTHORIZED
        and: 'the refusal carries nothing of the record'
        resp.body() == null

        where:
        ROUTE                                  | CALL
        '/v1alpha1/builds/{id}'                | { it.builds(it.access).getBuildRecord('bd-1_1', null, null) }
        '/v1alpha1/builds/{id}/status'         | { it.builds(it.access).getBuildStatus('bd-1_1', null, null) }
        '/v1alpha1/builds/{id}/logs'           | { it.builds(it.access).getBuildLog('bd-1_1', null, null) }
        '/v1alpha1/builds/{id}/condalock'      | { it.builds(it.access).getCondaLock('bd-1_1', null, null) }
        '/v1alpha1/scans/{id}'                 | { it.scans(it.access).scanImage('sc-1', null, null) }
        '/v1alpha1/scans/{id}/spdx'            | { it.scans(it.access).getSbomSPDX('sc-1', null, null) }
        '/v1alpha1/scans/{id}/logs'            | { it.scans(it.access).getScanLog('sc-1', null, null) }
        '/v1alpha1/mirrors/{id}'               | { it.mirrors(it.access).getMirrorRecord('mr-1', null, null) }
        '/v1alpha1/mirrors/{id}/logs'          | { it.mirrors(it.access).getScanLog('mr-1', null, null) }
        '/container-token/{token}'             | { it.containers(it.access).describeContainerRequest('tok', null, null) }
        '/v1alpha2/container/{id}'             | { it.containers(it.access).getContainerDetails('cr-1', null, null) }
        '/v1alpha2/container/{id}/status'      | { it.containers(it.access).getContainerStatus('cr-1', null, null) }
    }

    def 'should serve a public record on the deprecated routes without a token' () {
        given:
        access = Mock(RecordAccessService) { check(_, _, _) >> RecordAccessService.Decision.ALLOW }

        expect:
        builds(access).getBuildRecord('bd-1_1', null, null).status() == HttpStatus.OK
        builds(access).getBuildStatus('bd-1_1', null, null).status() == HttpStatus.OK
        mirrors(access).getMirrorRecord('mr-1', null, null).status() == HttpStatus.OK
    }

}
