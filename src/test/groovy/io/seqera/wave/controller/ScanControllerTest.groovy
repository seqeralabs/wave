/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ScanState
import io.seqera.wave.service.scan.ScanVulnerability
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ScanControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject PersistenceService persistenceService

    def "should return 200 and WaveContainerScanRecord "() {
        given:
        def containerScanService = Mock(ContainerScanService)
        def startTime = Instant.now()
        def duration = Duration.ofMinutes(2)
        def scanId = '123'
        def buildId = "testbuildid"
        def containerImage = "testcontainerimage"
        def scanVulnerability = new ScanVulnerability(
                "id1",
                "low",
                "title",
                "pkgname",
                "installed.version",
                "fix.version",
                "url")
        def results = List.of(scanVulnerability)
        def scanRecord = new WaveScanRecord(
                                                buildId,
                                                new ScanState(
                                                        scanId,
                                                        buildId,
                                                        containerImage,
                                                        startTime,
                                                        duration,
                                                        'SUCCEEDED',
                                                        results))
        and:
        persistenceService.createScanRecord(scanRecord)

        when:
        def req = HttpRequest.GET("/v1alpha1/scans/${scanRecord.id}")
        def res = client.toBlocking().exchange(req, WaveScanRecord)

        then:
        res.body().id == scanRecord.id
        res.body().buildId == scanRecord.buildId
    }


    def "should return 404 and null"() {
        when:
        def req = HttpRequest.GET("/v1alpha1/scans/unknown")
        def res = client.toBlocking().exchange(req, WaveScanRecord)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 404
    }
}
