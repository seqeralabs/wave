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

package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant

import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import com.fasterxml.jackson.annotation.JsonIgnore
import io.seqera.wave.api.BuildCompression
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.core.ChildRefs
import io.seqera.wave.service.builder.BuildEntry
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.tower.RecordOwner
/**
 * A collection of request and response properties to be stored
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ToString(includePackage = false, includeNames = true)
@AutoClone
@CompileStatic
@EqualsAndHashCode
class WaveBuildRecord {

    @PostgresIgnore
    String buildId
    String dockerFile
    String condaFile
    String targetImage
    String userName
    String userEmail
    Long userId
    String requestIp
    Instant startTime
    String offsetId
    Duration duration
    Integer exitStatus
    String platform
    String scanId
    BuildFormat format
    String digest
    BuildCompression compression
    String buildTemplate
    ChildRefs buildChildIds
    ChildRefs scanChildIds
    @JsonIgnore @PersistOnly RecordOwner owner   // stored, not returned by the API
    String visibility                            // a Visibility value; null is read as public

    Boolean succeeded() {
        return duration != null ? (exitStatus==0) : null
    }

    Boolean done() {
        return duration != null
    }

    static WaveBuildRecord fromEntry(BuildEntry entry) {
        create0(entry.request, entry.result)
    }
    
    static WaveBuildRecord fromEvent(BuildEvent event) {
        create0(event.request, event.result)
    }
    
    static private WaveBuildRecord create0(BuildRequest request, BuildResult result) {
        if( result && request.buildId != result.buildId )
            throw new IllegalStateException("Build id must match the result id")
        return new WaveBuildRecord(
                buildId: request.buildId,
                dockerFile: request.containerFile,
                condaFile: request.condaFile,
                targetImage: request.targetImage,
                userName: request.identity.user?.userName,
                userEmail: request.identity.user?.email,
                userId: request.identity.user?.id,
                requestIp: request.ip,
                startTime: request.startTime,
                platform: request.platform?.toString(),
                offsetId: request.offsetId,
                scanId: request.scanId,
                format: request.format,
                duration: result?.duration,
                exitStatus: result?.exitStatus,
                digest: result?.digest,
                compression: request?.compression,
                buildTemplate: request?.buildTemplate,
                buildChildIds: request?.buildChildIds,
                scanChildIds: request?.scanChildIds,
                owner: RecordOwner.of(request.identity),
                visibility: request.visibility
        )
    }
    
    /**
     * @return A copy of this record exposing only the user id, so that the user name, the email
     *         and the request IP address are not leaked when the record is returned in an API response.
     */
    WaveBuildRecord withUserIdOnly() {
        if( userName==null && userEmail==null && requestIp==null )
            return this
        final result = this.clone()
        result.userName = null
        result.userEmail = null
        result.requestIp = null
        return result
    }

    BuildStatusResponse toStatusResponse() {
        final status = duration!=null
                ? BuildStatusResponse.Status.COMPLETED
                : BuildStatusResponse.Status.PENDING

        return new BuildStatusResponse(
                buildId,
                status,
                startTime,
                duration,
                succeeded() )
    }

}
