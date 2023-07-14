package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpResponse
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.model.ScanResult
import io.seqera.wave.model.ScanVulnerability
import io.seqera.wave.service.ContainerScanService
import io.seqera.wave.service.persistence.WaveScanRecord
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name="wave.scan.workspace",value="scan-test-workspace")
class ContainerScanControllerTest extends Specification {

    def "should return 200 and WaveContainerScanRecord "() {
        given:
        def containerScanService = Mock(ContainerScanService)
        def startTime = Instant.now()
        def duration = Duration.ofMinutes(2)
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
        def waveContainerScanRecord = new WaveScanRecord(
                                                buildId,
                                                new ScanResult(
                                                        buildId,
                                                        startTime,
                                                        results,
                                                        duration,
                                                    true))
        def containerScanController = new ContainerScanController(containerScanService)

        when:
        def controllerResult = containerScanController.scanImage(buildId)

        then:
        1*containerScanService.getScanResult(buildId) >> waveContainerScanRecord
        controllerResult.status == HttpResponse.ok().status
        controllerResult.body.get() == waveContainerScanRecord
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
