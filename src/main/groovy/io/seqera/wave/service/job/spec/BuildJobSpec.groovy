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

package io.seqera.wave.service.job.spec

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.CleanableAware
import io.seqera.wave.tower.PlatformId

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class BuildJobSpec implements JobSpec, CleanableAware {

    final BuildRequest request

    Type getType() {
        return Type.Build
    }

    @Override
    String getId() {
        return request.getTargetImage()
    }

    @Override
    Instant getCreationTime() {
        return request.getStartTime()
    }

    @Override
    Duration getMaxDuration() {
        return request.getMaxDuration()
    }

    @Override
    String getSchedulerId() {
        return "build-" + request.buildId.replace('_', '-')
    }

    String getBuildId() {
        return request.buildId
    }

    String getTargetImage() {
        return request.getTargetImage()
    }

    PlatformId getIdentity() {
        return request.getIdentity()
    }

    Path getCleanableDir() {
        return request.getWorkDir()
    }
}
