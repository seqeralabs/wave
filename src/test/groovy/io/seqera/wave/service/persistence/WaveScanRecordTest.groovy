package io.seqera.wave.service.persistence

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.scan.ScanResult
import io.seqera.wave.service.scan.ScanVulnerability

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class WaveScanRecordTest extends Specification {

    def 'should create wave scan record' () {
        given:
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
        def scanresult= new ScanResult(
                buildId,
                startTime,
                results,
                duration,
                true)

        when:
        def waveContainerScanRecord = new WaveScanRecord(buildId, scanresult)

        then:
        waveContainerScanRecord.id == buildId
        waveContainerScanRecord.vulnerabilities[0] == scanVulnerability
    }
}
