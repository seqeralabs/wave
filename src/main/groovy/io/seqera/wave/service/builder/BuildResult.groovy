package io.seqera.wave.service.builder

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.Memoized
import groovy.transform.ToString
/**
 * Model a container builder request
 *
 * WARNING: this class is stored as JSON serialized object in the {@link BuildStore}.
 * Make sure changes are backward compatible with previous object versions
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
@CompileStatic
class BuildResult {

    final String id
    final int exitStatus
    final String logs
    final Instant startTime
    final Duration duration

    BuildResult(String id, int exitStatus, String content, Instant startTime, Duration duration) {
        this.id = id
        this.logs = content?.replaceAll("\u001B\\[[;\\d]*m", "") // strip ansi escape codes
        this.exitStatus = exitStatus
        this.startTime = startTime
        this.duration = duration
    }

    /* Do not remove - required by jackson de-ser */
    protected BuildResult() {}

    String getId() { id }

    Duration getDuration() { duration }

    int getExitStatus() { exitStatus }

    Instant getStartTime() { startTime }

    String getLogs() { logs }

    boolean done() { duration!=null }

    boolean succeeded() { done() && exitStatus==0 }

    boolean failed() { done() && exitStatus!=0 }

    @Override
    String toString() {
        return "BuildRequest[id=$id; exitStatus=$exitStatus; duration=$duration]"
    }

    static BuildResult completed(String id, int exitStatus, String content, Instant startTime) {
        new BuildResult(id, exitStatus, content, startTime, Duration.between(startTime, Instant.now()))
    }

    static BuildResult failed(String id, String content, Instant startTime) {
        new BuildResult(id, -1, content, startTime, Duration.between(startTime, Instant.now()))
    }

    static BuildResult create(BuildRequest req) {
        new BuildResult(req.id, 0, null, req.startTime, null)
    }

    static BuildResult create(String id) {
        new BuildResult(id, 0, null, Instant.now(), null)
    }

    @Memoized
    static BuildResult unknown() {
        new BuildResult('-', -1, 'Unknown build status', null as Instant, Duration.ZERO)
    }
}
