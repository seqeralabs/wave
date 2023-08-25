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
        def scanId = '12345'
        def buildId = "testbuildid"
        def scanVulnerability = new ScanVulnerability(
                                "id1",
                                "low",
                                "title",
                                "pkgname",
                                "installed.version",
                                "fix.version",
                                "url")
        def vulns = List.of(scanVulnerability)

        when:
        def scanResult= new ScanResult(
                scanId,
                buildId,
                startTime,
                duration,
                'SUCCEEDED',
                vulns)
        then:
        scanResult.id == scanId
        scanResult.buildId == buildId
        scanResult.isCompleted()
        scanResult.isSucceeded()

        when:
        def scanRecord = new WaveScanRecord(scanId, scanResult)

        then:
        scanRecord.id == scanId
        scanRecord.buildId == buildId
        scanRecord.vulnerabilities[0] == scanVulnerability
    }
}
