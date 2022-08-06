package io.seqera.wave.service.builder

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
/**
 * Model a container builder request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class BuildResult {

    public static final BuildResult ERROR = new BuildResult('-', -1, 'Unknown', null, Duration.ZERO)

    final String id
    final int exitStatus
    final String logs
    final Instant startTime
    final Duration duration

    BuildResult(String id, int exitStatus, String content, Instant startTime, Duration duration=null) {
        this.id = id
        this.logs = content
        this.exitStatus = exitStatus
        this.startTime = startTime
        this.duration = duration ?: Duration.between(startTime, Instant.now())
    }

    String getId() { id }

    Duration getDuration() { duration }

    int getExitStatus() { exitStatus }

    Instant getStartTime() { startTime }

    String getLogs() { logs }

    @Override
    String toString() {
        return "BuildRequest[id=$id; exitStatus=$exitStatus; duration=$duration]"
    }
}
