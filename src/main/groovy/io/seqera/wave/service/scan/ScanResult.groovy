package io.seqera.wave.service.scan

import java.time.Duration
import java.time.Instant

/**
 * Model for scan result
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
@CompileStatic
class ScanResult {
    String buildId
    Instant startTime
    Duration duration
    boolean isSuccess
    List<ScanVulnerability> vulnerabilities

    private ScanResult(String buildId, Instant startTime, List<ScanVulnerability> vulnerabilities, Duration duration, boolean isSuccess) {
        this.buildId = buildId
        this.startTime = startTime
        this.duration = duration
        this.vulnerabilities = vulnerabilities
        this.isSuccess = isSuccess
    }

    static ScanResult success(String buildId, Instant startTime, List<ScanVulnerability> vulnerabilities){
        return new ScanResult(buildId, startTime, vulnerabilities, Duration.between(startTime, Instant.now()), true)
    }

    static ScanResult failure(String buildId, Instant startTime, List<ScanVulnerability> vulnerabilities){
        return new ScanResult(buildId, startTime, vulnerabilities, Duration.between(startTime, Instant.now()), false)
    }

    static ScanResult create(String buildId, Instant startTime, Duration duration1, boolean isSuccess, List<ScanVulnerability> vulnerabilities){
        return new ScanResult(buildId, startTime, vulnerabilities, duration1, isSuccess)
    }
}
