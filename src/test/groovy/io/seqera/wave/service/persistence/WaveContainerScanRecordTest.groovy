package io.seqera.wave.service.persistence

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.model.ScanResult
import io.seqera.wave.model.ScanVulnerability

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class WaveContainerScanRecordTest extends Specification {
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
        def waveContainerScanRecord = new WaveContainerScanRecord(buildId, scanresult)

        then:
        waveContainerScanRecord.buildId == buildId
        waveContainerScanRecord.scanVulnerabilitiesIds[0] == "id1"
    }
}
