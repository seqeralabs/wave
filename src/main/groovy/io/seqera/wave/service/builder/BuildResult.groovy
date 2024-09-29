/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
 * WARNING: this class is stored as JSON serialized object in the {@link BuildStateStore}.
 * Make sure changes are backward compatible with previous object versions
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
@CompileStatic
class BuildResult {

    final String id
    final Integer exitStatus
    final String logs
    final Instant startTime
    final Duration duration
    final String digest

    BuildResult(String id, Integer exitStatus, String logs, Instant startTime, Duration duration, String digest) {
        this.id = id
        this.logs = logs?.replaceAll("\u001B\\[[;\\d]*m", "") // strip ansi escape codes
        this.exitStatus = exitStatus
        this.startTime = startTime
        this.duration = duration
        this.digest = digest
    }

    /* Do not remove - required by jackson de-ser */
    protected BuildResult() {}

    String getId() { id }

    Duration getDuration() { duration }

    Integer getExitStatus() { exitStatus }

    Instant getStartTime() { startTime }

    String getLogs() { logs }

    boolean done() { duration!=null }

    boolean succeeded() { done() && exitStatus==0 }

    boolean failed() { done() && exitStatus!=0 }

    @Override
    String toString() {
        return "BuildResult[id=$id; exitStatus=$exitStatus; duration=$duration]"
    }

    static BuildResult completed(String buildId, Integer exitStatus, String logs, Instant startTime, String digest) {
        new BuildResult(buildId, exitStatus, logs, startTime, Duration.between(startTime, Instant.now()), digest)
    }

    static BuildResult failed(String buildId, String logs, Instant startTime) {
        new BuildResult(buildId, -1, logs, startTime, Duration.between(startTime, Instant.now()), null)
    }

    static BuildResult create(BuildRequest req) {
        new BuildResult(req.buildId, null, null, req.startTime, null, null)
    }

    static BuildResult create(String buildId) {
        new BuildResult(buildId, null, null, Instant.now(), null, null)
    }

    @Memoized
    static BuildResult unknown() {
        new BuildResult('-', -1, 'Unknown build status', null as Instant, Duration.ZERO, null)
    }
}
