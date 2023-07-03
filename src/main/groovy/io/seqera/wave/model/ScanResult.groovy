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

@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
@CompileStatic
class ScanResult {
    private String buildId
    private Instant startTime
    private Duration duration

    void setStartTime(Instant startTime) {
        this.startTime = startTime
    }

    void setDuration(Duration duration) {
        this.duration = duration
    }

    void setResult(String result) {
        this.result = result
    }
    private String result

    String getBuildId() {
        return buildId
    }

    void setBuildId(String buildId) {
        this.buildId = buildId
    }

    Instant getStartTime() {
        return startTime
    }

    Duration getDuration() {
        return duration
    }

    String getResult() {
        return result
    }
}
