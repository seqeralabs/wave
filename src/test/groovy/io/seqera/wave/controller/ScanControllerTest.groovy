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

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.tower.RecordOwner
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
        def startTime = Instant.now()
        def duration = Duration.ofMinutes(2)
        def platform = ContainerPlatform.DEFAULT
        def scanId = 'sc123'
        def buildId = "bd-123"
        def mirrorId = 'mr-123'
        def requestId = 'rq-123'
        def containerImage = "testcontainerimage"
        def workDir = Path.of('/some/work/dir')
        def configJson = '{auth}'
        def scanVulnerability = new ScanVulnerability(
                "id1",
                "low",
                "title",
                "pkgname",
                "installed.version",
                "fix.version",
                "url")
        def results = List.of(scanVulnerability)
        def scan = new WaveScanRecord(
                        new ScanEntry(
                                scanId,
                                buildId,
                                mirrorId,
                                requestId,
                                containerImage,
                                platform,
                                workDir,
                                configJson,
                                startTime,
                                duration,
                                'SUCCEEDED',
                                results))
        and:
        persistenceService.saveScanRecordAsync(scan)

        when:
        def req = HttpRequest.GET("/v1alpha1/scans/${scan.id}")
        def res = client.toBlocking().exchange(req, WaveScanRecord)

        then:
        res.body().id == scan.id
        res.body().buildId == scan.buildId
        res.body().mirrorId == scan.mirrorId
        res.body().requestId == scan.requestId
    }

    def "should return 404 and null"() {
        when:
        def req = HttpRequest.GET("/v1alpha1/scans/unknown")
        client.toBlocking().exchange(req, WaveScanRecord)

        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 404
    }

    def "should return 200 and logs "() {
        given:
        def startTime = Instant.now()
        def duration = Duration.ofMinutes(2)
        def platform = ContainerPlatform.DEFAULT
        def scanId = 'sc123'
        def buildId = "bd-123"
        def mirrorId = 'mr-123'
        def requestId = 'rq-123'
        def containerImage = "testcontainerimage"
        def workDir = Path.of('/some/work/dir')
        def configJson = '{auth}'
        def scan = new WaveScanRecord(
                new ScanEntry(
                        scanId,
                        buildId,
                        mirrorId,
                        requestId,
                        containerImage,
                        platform,
                        workDir,
                        configJson,
                        startTime,
                        duration,
                        'FAILED',
                        null,
                        1,
                        'scan failed'))
        and:
        persistenceService.saveScanRecordAsync(scan)

        when:
        def req = HttpRequest.GET("/v1alpha1/scans/${scan.id}/logs")
        def res = client.toBlocking().exchange(req, String)

        then:
        res.status.code == 200
        res.header("Content-Disposition") == "attachment; filename=${scanId}.log"

        and:
        res.body() == "scan failed"
    }

    def "should not return the owner in the scan record response"() {
        given:
        def owner = new RecordOwner(towerEndpoint: 'https://api.cloud.seqera.io', userId: 100L, workspaceId: 200L)
        def scan = new WaveScanRecord(
                id: 'sc-owner123',
                buildId: 'bd-owner123_1',
                containerImage: 'testcontainerimage',
                status: 'SUCCEEDED',
                startTime: Instant.now(),
                owner: owner )
        and:
        persistenceService.saveScanRecordAsync(scan)

        when: 'the record is read back over HTTP, as a client sees it'
        def res = client.toBlocking().exchange(HttpRequest.GET("/v1alpha1/scans/${scan.id}"), String)

        then:
        res.status.code == 200
        res.body().contains(scan.id)
        and: 'the owner is absent from the response'
        !res.body().contains('"owner"')
        !res.body().contains('api.cloud.seqera.io')
        and: 'while the stored record still carries it'
        persistenceService.loadScanRecord(scan.id).owner == owner
    }
}
