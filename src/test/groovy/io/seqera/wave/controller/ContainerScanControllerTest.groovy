package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpResponse
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ScanResult
import io.seqera.wave.service.scan.ScanVulnerability
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerScanControllerTest extends Specification {

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
        def scanController = new ContainerScanController(containerScanService)

        when:
        def controllerResult = scanController.scanImage(buildId)

        then:
        1*containerScanService.getScanResult(buildId) >> scanRecord
        controllerResult.status == HttpResponse.ok().status
        controllerResult.body.get() == scanRecord
    }
    def "should return 404 and null"() {
        given:
        def containerScanService = Mock(ContainerScanService)
        def buildId = "testbuildid"
        containerScanService.getScanResult(buildId) >> null
        def containerScanController = new ContainerScanController(containerScanService)

        when:
        def controllerResult = containerScanController.scanImage(buildId)

        then:
        1*containerScanService.getScanResult(buildId)
        controllerResult.status == HttpResponse.notFound().status
    }
}
