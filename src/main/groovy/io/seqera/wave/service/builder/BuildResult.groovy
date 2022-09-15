package io.seqera.wave.service.builder

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Model a container builder request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
@CompileStatic
class BuildResult {

    public static final BuildResult UNKNOWN = new BuildResult('-', -1, 'Unknown', null as Instant, Duration.ZERO)

    final String id
    final int exitStatus
    final String logs
    final Instant startTime
    final Duration duration

    BuildResult(String id, int exitStatus, String content, Instant startTime, Duration duration=null) {
        this.id = id
        this.logs = content?.replaceAll("\u001B\\[[;\\d]*m", "") // strip ansi escape codes
        this.exitStatus = exitStatus
        this.startTime = startTime
        this.duration = duration ?: Duration.between(startTime, Instant.now())
    }

    /* Do not remove - required by jackson de-ser */
    protected BuildResult() {}

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
