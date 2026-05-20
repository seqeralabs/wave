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
import io.seqera.wave.api.v1.model.ContainerMirrorResponse
import io.seqera.wave.api.v1.model.ContainerPlatform as V1ContainerPlatform
import io.seqera.wave.api.v1.model.Status
import io.seqera.wave.core.ContainerPlatform as InternalContainerPlatform
import io.seqera.wave.service.mirror.MirrorResult

/**
 * Maps internal {@link MirrorResult} model objects to their v1 API counterparts.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class MirrorsV1Mapper {

    /**
     * Map an internal {@link MirrorResult} to a v1 {@link ContainerMirrorResponse}.
     * <p>
     * The {@code logs} field is intentionally excluded — logs are served separately
     * via the {@code /w1/mirrors/{id}/logs} route, not as part of the record JSON.
     *
     * @param internal  the internal mirror result (must not be null)
     * @return a v1 model response with all available fields populated
     */
    static ContainerMirrorResponse toV1(MirrorResult internal) {
        return new ContainerMirrorResponse()
                .mirrorId(internal.mirrorId)
                .digest(internal.digest)
                .sourceImage(internal.sourceImage)
                .targetImage(internal.targetImage)
                .platform(toV1Platform(internal.platform))
                .creationTime(internal.creationTime?.toString())
                .status(toV1Status(internal.status))
                .duration(internal.duration?.toString())
                .exitCode(internal.exitCode)
    }

    private static V1ContainerPlatform toV1Platform(InternalContainerPlatform platform) {
        if( platform == null )
            return null
        // Use the first platform entry for the v1 single-platform model
        final p = platform.platforms[0]
        return new V1ContainerPlatform()
                .os(p.os)
                .arch(p.arch)
                .variant(p.variant)
    }

    private static Status toV1Status(MirrorResult.Status status) {
        if( status == null )
            return null
        return Status.valueOf(status.name())
    }
}
