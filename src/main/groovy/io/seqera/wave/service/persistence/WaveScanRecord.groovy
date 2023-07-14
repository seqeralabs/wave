package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.model.ScanResult
import io.seqera.wave.model.ScanVulnerability
/**
 * Model a Wave container scan result
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class WaveScanRecord {
    String id
    Instant startTime
    Duration duration
    boolean isSuccess
    List<ScanVulnerability> vulnerabilities

    WaveScanRecord(){}

    WaveScanRecord(String id, Instant startTime, Duration duration, boolean isSuccess, List<ScanVulnerability> vulnerabilities) {
        this.id = id
        this.startTime = startTime
        this.duration = duration
        this.isSuccess = isSuccess
        this.vulnerabilities = vulnerabilities
                ? new ArrayList<ScanVulnerability>(vulnerabilities)
                : List.<ScanVulnerability>of()
    }

    WaveScanRecord(String id, ScanResult scanResult) {
        this.id = id
        this.startTime = scanResult.startTime
        this.duration = scanResult.duration
        this.isSuccess = scanResult.isSuccess
        this.vulnerabilities = scanResult.vulnerabilities
                ? new ArrayList<ScanVulnerability>(scanResult.vulnerabilities)
                : List.<ScanVulnerability>of()
    }
}
