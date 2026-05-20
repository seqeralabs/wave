/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1.mapper

import groovy.transform.CompileStatic
import io.seqera.wave.api.BuildStatusResponse as InternalBuildStatusResponse
import io.seqera.wave.api.v1.model.BuildStatusResponse as V1BuildStatusResponse
import io.seqera.wave.api.v1.model.Status
import io.seqera.wave.api.v1.model.WaveBuildRecord as V1WaveBuildRecord
import io.seqera.wave.api.v1.model.WaveBuildRecordFormat
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.persistence.WaveBuildRecord as InternalWaveBuildRecord

/**
 * Maps internal model objects to their v1 API counterparts for the builds API.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class BuildsV1Mapper {

    /**
     * Map an internal {@link InternalWaveBuildRecord} to a v1 {@link V1WaveBuildRecord}.
     *
     * @param internal  the internal persistence record (must not be null)
     * @return a v1 model record with all available fields populated
     */
    static V1WaveBuildRecord toV1Record(InternalWaveBuildRecord internal) {
        return new V1WaveBuildRecord()
                .buildId(internal.buildId)
                .dockerFile(internal.dockerFile)
                .condaFile(internal.condaFile)
                .targetImage(internal.targetImage)
                .userName(internal.userName)
                .userEmail(internal.userEmail)
                .userId(internal.userId)
                .requestIp(internal.requestIp)
                .startTime(internal.startTime?.toString())
                .offsetId(internal.offsetId)
                .duration(internal.duration?.toMillis())
                .exitStatus(internal.exitStatus)
                .format(toV1Format(internal.format))
                .platform(internal.platform)
                .scanId(internal.scanId)
                .digest(internal.digest)
                .succeeded(internal.succeeded())
    }

    /**
     * Map an internal {@link InternalBuildStatusResponse} to a v1 {@link V1BuildStatusResponse}.
     *
     * @param internal  the internal status response (must not be null)
     * @return a v1 model status response
     */
    static V1BuildStatusResponse toV1Status(InternalBuildStatusResponse internal) {
        return new V1BuildStatusResponse()
                .id(internal.id)
                .status(mapStatusEnum(internal.status))
                .startTime(internal.startTime?.toString())
                .duration(internal.duration?.toString())
                .succeeded(internal.succeeded)
    }

    private static WaveBuildRecordFormat toV1Format(BuildFormat format) {
        if( format == null )
            return null
        switch (format) {
            case BuildFormat.DOCKER:      return WaveBuildRecordFormat.DOCKER
            case BuildFormat.SINGULARITY: return WaveBuildRecordFormat.SIF
            default:
                throw new IllegalArgumentException("Unknown BuildFormat: ${format}")
        }
    }

    private static Status mapStatusEnum(InternalBuildStatusResponse.Status status) {
        if( status == null )
            return null
        return Status.valueOf(status.name())
    }
}
