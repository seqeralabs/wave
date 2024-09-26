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

package io.seqera.wave.service.buildstatus

import groovy.transform.CompileStatic
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.scan.ContainerScanService
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class BuildStatusServiceImpl implements BuildStatusService{

    @Inject
    private ContainerBuildService buildService

    @Inject
    private ContainerMirrorService mirrorService

    @Inject
    private ContainerScanService scanService

    BuildStatusServiceImpl() {}


    @Override
    BuildStatusResponse getBuildStatus(String requestId) {
        // build IDs starting with the `mr-` prefix are interpreted as mirror requests
        if( requestId.startsWith(MirrorRequest.ID_PREFIX) ) {
            handleMirrorRequest(requestId)
            return mirrorService
                    .getMirrorEntry(requestId)
                    ?.toStatusResponse()
        }
        else {
            handleBuildRequest(requestId)
            return buildService
                    .getBuildRecord(requestId)
                    ?.toStatusResponse()
        }
    }
}
