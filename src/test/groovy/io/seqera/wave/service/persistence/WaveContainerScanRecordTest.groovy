package io.seqera.wave.service.persistence

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.model.ScanResult
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
        def result = Map.of("key1","value1","key2","value2")
        def results = List.of(result)
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
        waveContainerScanRecord.scanResult == scanresult

    }
}
