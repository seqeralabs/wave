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

import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.v1.model.ScanSubmitRequest
import io.seqera.wave.api.v1.model.ScanSubmitResponse
import io.seqera.wave.api.v1.model.WaveScanRecord
import io.seqera.wave.service.persistence.WaveScanRecord as InternalWaveScanRecord
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ContainerScanServiceImpl
import io.seqera.wave.service.scan.ScanVulnerability
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Spock spec for {@link ScansV1Controller} – covers all four /w1/scans/* routes.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ScansV1ControllerSpec extends Specification {

    @Inject @Client('/') HttpClient client
    @Inject ContainerScanService scanService

    @MockBean(ContainerScanServiceImpl)
    ContainerScanService mockScanService() { Mock(ContainerScanService) }

    // ----------------------------------------------------------------
    // Helper to build a minimal internal WaveScanRecord
    // ----------------------------------------------------------------
    private InternalWaveScanRecord makeRecord(String id) {
        new InternalWaveScanRecord(
                id,
                'build-001',
                null,
                null,
                'docker.io/library/ubuntu:22.04',
                'linux/amd64',
                Instant.parse('2025-01-01T00:00:00Z'),
                Duration.ofSeconds(30),
                'SUCCEEDED',
                [new ScanVulnerability('CVE-2021-1234', 'HIGH', 'Test vuln', 'libfoo', '1.0', '1.1', 'https://nvd.nist.gov/vuln/detail/CVE-2021-1234')],
                0,
                'Scan completed successfully',
                null
        )
    }

    // ----------------------------------------------------------------
    // POST /w1/scans – happy path (submit with containerImage)
    // ----------------------------------------------------------------
    def 'POST /w1/scans returns 200 with scan id and target image'() {
        given:
        def req = new ScanSubmitRequest().containerImage('docker.io/library/ubuntu:22.04')
        def record = makeRecord('sc-abc123_1')
        scanService.submitScan(_) >> record

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.POST('/w1/scans', req), ScanSubmitResponse)

        then:
        resp.status == HttpStatus.OK
        resp.body().scanId == 'sc-abc123_1'
        resp.body().targetImage == 'docker.io/library/ubuntu:22.04'
    }

    // ----------------------------------------------------------------
    // POST /w1/scans – missing target → 400
    // ----------------------------------------------------------------
    def 'POST /w1/scans returns 400 when no target is provided'() {
        given:
        def req = new ScanSubmitRequest()   // no containerImage, buildId or mirrorId

        when:
        client.toBlocking().exchange(HttpRequest.POST('/w1/scans', req), ScanSubmitResponse)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }

    // ----------------------------------------------------------------
    // POST /w1/scans – multiple targets → 400
    // ----------------------------------------------------------------
    def 'POST /w1/scans returns 400 when multiple targets are provided'() {
        given:
        def req = new ScanSubmitRequest()
                .containerImage('docker.io/library/ubuntu:22.04')
                .buildId('build-001')

        when:
        client.toBlocking().exchange(HttpRequest.POST('/w1/scans', req), ScanSubmitResponse)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }

    // ----------------------------------------------------------------
    // GET /w1/scans/{id} – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/scans/{id} returns 200 with mapped WaveScanRecord'() {
        given:
        def id = 'sc-abc123_1'
        scanService.getScanRecord(id) >> makeRecord(id)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/scans/${id}"), WaveScanRecord)

        then:
        resp.status == HttpStatus.OK
        resp.body().id == id
        resp.body().buildId == 'build-001'
        resp.body().containerImage == 'docker.io/library/ubuntu:22.04'
        resp.body().status == 'SUCCEEDED'
        resp.body().duration == 30_000L
        resp.body().vulnerabilities.size() == 1
        resp.body().vulnerabilities[0].severity == 'HIGH'
        resp.body().vulnerabilities[0].count == 1
    }

    // ----------------------------------------------------------------
    // GET /w1/scans/{id} – not found
    // ----------------------------------------------------------------
    def 'GET /w1/scans/{id} returns 404 when record not found'() {
        given:
        scanService.getScanRecord('missing') >> null

        when:
        client.toBlocking().exchange(HttpRequest.GET('/w1/scans/missing'), WaveScanRecord)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // ----------------------------------------------------------------
    // GET /w1/scans/{id}/logs – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/scans/{id}/logs returns 200 with log text and Content-Disposition header'() {
        given:
        def id = 'sc-abc123_1'
        scanService.getScanRecord(id) >> makeRecord(id)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/scans/${id}/logs"), String)

        then:
        resp.status == HttpStatus.OK
        resp.body() == 'Scan completed successfully'
        resp.header('Content-Disposition') == "attachment; filename=${id}.log"
    }

    // ----------------------------------------------------------------
    // GET /w1/scans/{id}/logs – not found
    // ----------------------------------------------------------------
    def 'GET /w1/scans/{id}/logs returns 404 when record not found'() {
        given:
        scanService.getScanRecord('missing') >> null

        when:
        client.toBlocking().exchange(HttpRequest.GET('/w1/scans/missing/logs'), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }

    // ----------------------------------------------------------------
    // GET /w1/scans/{id}/spdx – happy path
    // ----------------------------------------------------------------
    def 'GET /w1/scans/{id}/spdx returns 200 with SPDX content and Content-Disposition header'() {
        given:
        def id = 'sc-abc123_1'
        def spdxContent = '{"spdxVersion":"SPDX-2.3","dataLicense":"CC0-1.0"}'
        def streamedFile = new StreamedFile(
                new ByteArrayInputStream(spdxContent.bytes),
                MediaType.APPLICATION_OCTET_STREAM_TYPE)

        when:
        def resp = client.toBlocking()
                .exchange(HttpRequest.GET("/w1/scans/${id}/spdx"), String)

        then:
        1 * scanService.fetchReportStream(id, _) >> streamedFile
        resp.status == HttpStatus.OK
        resp.body() == spdxContent
    }

    // ----------------------------------------------------------------
    // GET /w1/scans/{id}/spdx – not found when no SPDX report
    // ----------------------------------------------------------------
    def 'GET /w1/scans/{id}/spdx returns 404 when SPDX report does not exist'() {
        given:
        def id = 'sc-abc123_1'

        when:
        client.toBlocking().exchange(HttpRequest.GET("/w1/scans/${id}/spdx"), String)

        then:
        1 * scanService.fetchReportStream(id, _) >> null
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
    }
}
