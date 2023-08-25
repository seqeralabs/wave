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
import io.seqera.wave.service.scan.ScanResult
import io.seqera.wave.service.scan.ScanVulnerability
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerScanControllerTest extends Specification {

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
                                                new ScanResult(
                                                        scanId,
                                                        buildId,
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
