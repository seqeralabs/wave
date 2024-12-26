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

import java.time.Instant
import java.time.OffsetDateTime

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.User
import static io.seqera.wave.util.DataTimeUtils.parseOffsetDateTime
/**
 * Model a Wave container request record 
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class WaveContainerRecord {

    /**
     * wave request id, this will be the token
     * This is container token and it is named as id for surrealdb requirement
     */
    String id

    /**
     * The Tower user associated with the request
     */
    User user

    /**
     * The Tower workspace associated with the request
     */
    Long workspaceId

    /**
     * The container image requested. this can be null null when a build request was submitted
     */
    String containerImage

    /**
     * The container file (aka Dockerfile) content associated with the request
     */
    String containerFile

    /**
     * The container config associated with the request
     */
    ContainerConfig containerConfig

    /**
     * The conda file associated with the request
     */
    String condaFile

    /**
     * The container arch platform
     */
    String platform

    /**
     * The Tower endpoint associated with the request
     */
    String towerEndpoint

    /**
     * The repository where the build image is uploaded
     */
    String buildRepository

    /**
     * The repository where container layers are cached
     */
    String cacheRepository

    /**
     * The request fingerprint
     */
    String fingerprint

    /**
     * The request timestamp
     */
    Instant timestamp

    /**
     * The time zone id where the request was originated
     */
    String zoneId

    /**
     * The IP address originating the request
     */
    String ipAddress

    /**
     * The container image associated with this Wave container, it can be the container image
     * as provide by the user, or a container image built by Wave
     */
    String sourceImage

    /**
     * The container SHA256 digest of the container image associated with this request
     */
    String sourceDigest

    /**
     * The resulting Wave container image name
     */
    String waveImage

    /**
     * The resulting Wave container image digest
     */
    String waveDigest

    /**
     * The timestamp of the Wave container expiration
     */
    Instant expiration

    /**
     * The ID of the build if the Wave request triggered a container build, null otherwise
     */
    String buildId

    /**
     * Whenever a new build was triggered for this Wave request, or the container was built by a previous request
     */
    Boolean buildNew

    /**
     * Whenever the request is a Wave container freeze
     */
    Boolean freeze

    /**
     * Whenever the request is for container with fusion
     */
    String fusionVersion

    /**
     * Whenever it's a "mirror" build request
     */
    Boolean mirror

    /**
     * The scan id associated with this request
     */
    String scanId

    WaveContainerRecord(SubmitContainerTokenRequest request, ContainerRequest data, String waveImage, String addr, Instant expiration) {
        this.id = data.requestId
        this.user = data.identity.user
        this.workspaceId = request.towerWorkspaceId
        this.containerImage = request.containerImage
        this.containerConfig = ContainerConfig.copy(request.containerConfig, true)
        this.platform = request.containerPlatform
        this.towerEndpoint = request.towerEndpoint
        this.buildRepository = request.buildRepository
        this.cacheRepository = request.cacheRepository
        this.fingerprint = request.fingerprint
        this.condaFile = data.condaFile
        this.containerFile = data.containerFile
        this.sourceImage = data.containerImage
        this.waveImage = waveImage
        this.expiration = expiration
        this.ipAddress = addr
        final ts = parseOffsetDateTime(request.timestamp) ?: OffsetDateTime.now()
        this.timestamp = ts?.toInstant()
        this.zoneId = ts?.getOffset()?.getId()
        this.buildId = data.buildId
        this.buildNew = data.buildId ? data.buildNew : null
        this.freeze = data.buildId ? data.freeze : null
        this.fusionVersion = request?.containerConfig?.fusionVersion()?.number
        this.mirror = data.mirror
        this.scanId = data.scanId
    }

    WaveContainerRecord(WaveContainerRecord that, String sourceDigest, String waveDigest) {
        this.id = that.id
        this.user = that.user
        this.workspaceId = that.workspaceId
        this.containerImage = that.containerImage
        this.containerConfig = that.containerConfig
        this.platform = that.platform
        this.towerEndpoint = that.towerEndpoint
        this.buildRepository = that.buildRepository
        this.cacheRepository = that.cacheRepository
        this.fingerprint = that.fingerprint
        this.timestamp = that.timestamp
        this.ipAddress = that.ipAddress
        this.condaFile = that.condaFile
        this.containerFile = that.containerFile
        this.sourceImage = that.sourceImage
        this.waveImage = that.waveImage
        this.expiration = that.expiration
        this.buildId = that.buildId
        this.buildNew = that.buildNew
        this.freeze = that.freeze
        this.fusionVersion = that.fusionVersion
        this.mirror == that.mirror
        this.scanId = that.scanId
        // -- digest part 
        this.sourceDigest = sourceDigest
        this.waveDigest = waveDigest
    }

    /**
     * Required by jackson ser/de-ser
     */
    protected WaveContainerRecord() { }

}
