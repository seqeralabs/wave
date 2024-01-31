/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat

/**
 * A collection of request and response properties to be stored
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ToString
@CompileStatic
@EqualsAndHashCode
class WaveBuildRecord {

    String buildId
    String dockerFile
    String condaFile
    String spackFile
    String spackArch
    String targetImage
    String userName
    String userEmail
    Long userId
    String requestIp
    Instant startTime
    String offsetId
    Duration duration
    int exitStatus
    String platform
    String scanId
    BuildFormat format

    boolean succeeded() { exitStatus==0 }

    static WaveBuildRecord fromEvent(BuildEvent event) {
        if( event.request.id != event.result.id )
            throw new IllegalStateException("Build id must match the result id")
        return new WaveBuildRecord(
                buildId: event.request.id,
                dockerFile: event.request.containerFile,
                condaFile: event.request.condaFile,
                spackFile: event.request.spackFile,
                spackArch: event.request.spackArch,
                targetImage: event.request.targetImage,
                userName: event.request.user?.userName,
                userEmail: event.request.user?.email,
                userId: event.request.user?.id,
                requestIp: event.request.ip,
                startTime: event.request.startTime,
                duration: event.result.duration,
                exitStatus: event.result.exitStatus,
                platform: event.request.platform,
                offsetId: event.request.offsetId,
                scanId: event.request.scanId,
                format: event.request.format
        )
    }
}
