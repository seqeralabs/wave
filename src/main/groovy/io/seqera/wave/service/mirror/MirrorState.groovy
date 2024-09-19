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

package io.seqera.wave.service.mirror

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.cache.StateRecord
import io.seqera.wave.service.job.JobRecord
import jakarta.inject.Singleton
/**
 * Model a container mirror result object
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Singleton
@CompileStatic
@Canonical
class MirrorState implements JobRecord, StateRecord {
    enum Status { PENDING, COMPLETED }

    final String mirrorId
    final String digest
    final String sourceImage
    final String targetImage
    final ContainerPlatform platform
    final Instant creationTime
    final Status status
    final Duration duration
    final Integer exitCode
    final String logs

    @Override
    String getRecordId() {
        return mirrorId
    }

    @Override
    boolean done() {
        status==Status.COMPLETED
    }

    boolean succeeded() {
        status==Status.COMPLETED && exitCode==0
    }

    MirrorState complete(Integer exitCode, String logs ) {
        new MirrorState(
                this.mirrorId,
                this.digest,
                this.sourceImage,
                this.targetImage,
                this.platform,
                this.creationTime,
                Status.COMPLETED,
                Duration.between(this.creationTime, Instant.now()),
                exitCode,
                logs
        )
    }

    static MirrorState from(MirrorRequest request) {
        new MirrorState(
                request.id,
                request.digest,
                request.sourceImage,
                request.targetImage,
                request.platform,
                request.creationTime,
                Status.PENDING
        )
    }

    BuildStatusResponse toStatusResponse() {
        final status = status == Status.COMPLETED
                ? BuildStatusResponse.Status.COMPLETED
                : BuildStatusResponse.Status.PENDING
        final succeeded = exitCode!=null
                ? exitCode==0
                : null
        return new BuildStatusResponse(
                mirrorId,
                status,
                creationTime,
                duration,
                succeeded )
    }
}
