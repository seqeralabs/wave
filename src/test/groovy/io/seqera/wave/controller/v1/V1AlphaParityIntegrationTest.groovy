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

package io.seqera.wave.controller.v1

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.logs.BuildLogServiceImpl
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.mirror.ContainerMirrorServiceImpl
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ContainerScanServiceImpl
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Side-by-side parity spec that exercises both the v1alpha1 (alpha) and /w1 (v1)
 * surfaces and verifies they are aligned:
 *
 * - GET /service-info vs GET /w1/service-info — full payload comparison
 * - GET /v1alpha1/builds/{id} vs GET /w1/builds/{id} — 404 parity for unknown id
 * - GET /v1alpha1/mirrors/{id} vs GET /w1/mirrors/{id} — 404 parity for unknown id
 * - GET /v1alpha1/scans/{id} vs GET /w1/scans/{id} — 404 parity for unknown id
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class V1AlphaParityIntegrationTest extends Specification {

    @Inject @Client('/') HttpClient client

    // -------------------------------------------------------------------
    // Mocks for shared services used by both alpha and v1 controllers
    // -------------------------------------------------------------------

    @MockBean(ContainerBuildService)
    ContainerBuildService mockBuildService() { Mock(ContainerBuildService) }

    @MockBean(BuildLogServiceImpl)
    BuildLogService mockBuildLogService() { Mock(BuildLogService) }

    @MockBean(ContainerScanServiceImpl)
    ContainerScanService mockScanService() { Mock(ContainerScanService) }

    @MockBean(ContainerMirrorServiceImpl)
    ContainerMirrorService mockMirrorService() { Mock(ContainerMirrorService) }

    // -------------------------------------------------------------------
    // service-info: full payload comparison
    // -------------------------------------------------------------------

    def 'GET /service-info and GET /w1/service-info return equivalent payloads'() {
        when:
        def alphaBody = client.toBlocking().retrieve(HttpRequest.GET('/service-info'), Map)
        def v1Body    = client.toBlocking().retrieve(HttpRequest.GET('/w1/service-info'), Map)

        then:
        // Both endpoints expose a serviceInfo object with the same version and commitId
        alphaBody.serviceInfo != null
        v1Body.serviceInfo != null
        alphaBody.serviceInfo.version   == v1Body.serviceInfo.version
        alphaBody.serviceInfo.commitId  == v1Body.serviceInfo.commitId
    }

    // -------------------------------------------------------------------
    // 404 parity for builds / mirrors / scans
    // -------------------------------------------------------------------

    @Unroll
    def 'GET #v1Path and GET #alphaPath return matching 404 for unknown id'() {
        when:
        def alphaStatus = try404(alphaPath)
        def v1Status    = try404(v1Path)

        then:
        alphaStatus == v1Status
        alphaStatus == HttpStatus.NOT_FOUND

        where:
        alphaPath                        | v1Path
        '/v1alpha1/builds/bd-unknown'    | '/w1/builds/bd-unknown'
        '/v1alpha1/mirrors/mr-unknown'   | '/w1/mirrors/mr-unknown'
        '/v1alpha1/scans/sc-unknown'     | '/w1/scans/sc-unknown'
    }

    // -------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------

    private HttpStatus try404(String path) {
        try {
            client.toBlocking().exchange(HttpRequest.GET(path))
            return HttpStatus.OK
        } catch (HttpClientResponseException ex) {
            return ex.status
        }
    }
}
