package io.seqera.wave.service.builder

import java.time.Duration
import java.time.Instant
import javax.annotation.Nullable

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.CompileStatic
/**
 * Model a container builder request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class BuildResult {

    public static final BuildResult UNKNOWN = new BuildResult('-', -1, 'Unknown', null as Instant, Duration.ZERO)

    final String id
    final int exitStatus
    final String logs
    final String startTimeStr
    final Duration duration

    BuildResult(String id, int exitStatus, String content, Instant startTime, Duration duration=null) {
        this(id, exitStatus, content, startTime?.toString(), duration)
    }

    @JsonCreator
    BuildResult(
            @JsonProperty("id")String id,
            @JsonProperty("exitStatus")int exitStatus,
            @JsonProperty("content")String content,
            @JsonProperty("startTimeStr")String startTimeStr,
            @JsonProperty("duration")Duration duration=null) {
        this.id = id
        this.logs = content?.replaceAll("\u001B\\[[;\\d]*m", "") // strip ansi escape codes
        this.exitStatus = exitStatus
        this.startTimeStr =  startTimeStr
        this.duration = duration// ?: Duration.between(startTime, Instant.now())
    }

    String getId() { id }

    Duration getDuration() { duration }

    int getExitStatus() { exitStatus }

    Instant getStartTime() { Instant.parse(startTimeStr) }

    String getLogs() { logs }

    @Override
    String toString() {
        return "BuildRequest[id=$id; exitStatus=$exitStatus; duration=$duration]"
    }
}
