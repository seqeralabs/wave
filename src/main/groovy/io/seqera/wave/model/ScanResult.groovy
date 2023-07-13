package io.seqera.wave.model

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
import io.seqera.wave.service.persistence.WaveContainerScanRecord

@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
@CompileStatic
class ScanResult {
    String buildId
    Instant startTime
    Duration duration
    List<ScanVulnerability> result
    boolean isSuccess

    private ScanResult(String buildId, Instant startTime, List<ScanVulnerability> result, Duration duration, boolean isSuccess) {
        this.buildId = buildId
        this.startTime = startTime
        this.duration = duration
        this.result = result
        this.isSuccess = isSuccess
    }

    static ScanResult success(String buildId, Instant startTime, List<ScanVulnerability> result){
        return new ScanResult(buildId, startTime, result, Duration.between(startTime, Instant.now()), true)
    }

    static ScanResult failure(String buildId, Instant startTime, List<ScanVulnerability> result){
        return new ScanResult(buildId, startTime, result, Duration.between(startTime, Instant.now()), false)
    }

    static ScanResult load(String buildId, Instant startTime, Duration duration1,  boolean isSuccess, List<ScanVulnerability> result){
        return new ScanResult(buildId, startTime, result, duration1, isSuccess)
    }
}
