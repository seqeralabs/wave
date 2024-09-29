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

package io.seqera.wave.service.request

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.service.builder.BuildEntry
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.WaveBuildRecord
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class ContainerState {

    final Instant startTime
    final Duration duration
    final Boolean succeeded

    boolean isRunning() {
        return duration==null
    }

    static ContainerState from(BuildEntry build) {
        assert build
        return new ContainerState(
                build.request.startTime,
                build.result?.duration,
                build.result?.succeeded()
        )
    }

    static ContainerState from(WaveBuildRecord build) {
        assert build
        return new ContainerState(
                build.startTime,
                build.duration,
                build.succeeded()
        )
    }

    static ContainerState from(MirrorEntry mirror) {
        assert mirror
        return new ContainerState(
                mirror.request.creationTime,
                mirror.result?.duration,
                mirror.result?.succeeded()
        )
    }

    static ContainerState from(MirrorResult mirror) {
        return new ContainerState(
                mirror.creationTime,
                mirror.duration,
                mirror.succeeded()
        )
    }
}
