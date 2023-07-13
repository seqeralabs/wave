package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant
import java.util.stream.Collectors

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.model.ScanResult

/**
 * Model a Wave container scan result
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class WaveContainerScanRecord {
    String buildId
    Instant startTime
    Duration duration
    boolean isSuccess
    List<String> scanVulnerabilitiesIds
    WaveContainerScanRecord(){}
    WaveContainerScanRecord(String buildId, Instant startTime, Duration duration, boolean isSuccess, List<String> scanVulnerabilitiesIds) {
        this.buildId = buildId
        this.startTime = startTime
        this.duration = duration
        this.isSuccess = isSuccess
        this.scanVulnerabilitiesIds = scanVulnerabilitiesIds
    }
    WaveContainerScanRecord(String buildId, ScanResult scanResult) {
        this.buildId = buildId
        this.startTime = scanResult.startTime
        this.duration = scanResult.duration
        this.isSuccess = scanResult.isSuccess
        if(scanResult.result!=null || !scanResult.result.isEmpty())
        this.scanVulnerabilitiesIds = scanResult.result.parallelStream()
                .map {it.vulnerabilityId}
                .collect(Collectors.toList())
    }
}
