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

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.seqera.wave.api.BuildCompression
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import static io.seqera.wave.service.builder.BuildFormat.DOCKER
import static io.seqera.wave.service.builder.BuildFormat.SINGULARITY
import static io.seqera.wave.util.StringUtils.trunc
/**
 * Model a container builder result
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode(includes = 'containerId,targetImage,buildId')
@CompileStatic
class BuildRequest {

    static final public String SEP = '_'

    static final public String ID_PREFIX = "bd-"

    /**
     * Unique request Id. This is computed as a consistent hash generated from
     * the container build assets e.g. Dockerfile. Therefore the same container build
     * request should result in the same `id`
     */
    final String containerId

    /**
     * The container file content corresponding to this request
     */
    final String containerFile

    /**
     * The conda file recipe associated with this request
     */
    final String condaFile

    /**
     * The build context work directory
     */
    final Path workspace

    /**
     * The target fully qualified image of the built container. It includes the target registry name
     */
    final String targetImage

    /**
     * The (tower) user made this request
     */
    final PlatformId identity

    /**
     * Container platform
     */
    final ContainerPlatform platform

    /**
     * Container repository for caching purposes
     */
    final String cacheRepository

    /**
     * Build request start time
     */
    final Instant startTime

    /**
     * The client IP if available
     */
    final String ip

    /**
     * Docker config json holding repository authentication
     */
    final String configJson

    /**
     * The time offset at the user timezone
     */
    final String offsetId

    /**
     * The associated {@link ContainerConfig} instance
     */
    final ContainerConfig containerConfig

    /**
     * The ID of the security scan triggered by this build 
     */
    final String scanId

    /**
     * Hold the build context for this container
     */
    final BuildContext buildContext

    /**
     * The target build format, either Docker or Singularity
     */
    final BuildFormat format

    /**
     * Max allow time duration for this build
     */
    final Duration maxDuration

    /**
     * The compression mode for the  container build
     */
    final BuildCompression compression

    /**
     * The build unique request id
     */
    final String buildId

    BuildRequest(
            String containerId,
            String containerFile,
            String condaFile,
            Path workspace,
            String targetImage,
            PlatformId identity,
            ContainerPlatform platform,
            String cacheRepository,
            String ip,
            String configJson,
            String offsetId,
            ContainerConfig containerConfig,
            String scanId,
            BuildContext buildContext,
            BuildFormat format,
            Duration maxDuration,
            BuildCompression compression
    )
    {
        this.containerId = containerId
        this.containerFile = containerFile
        this.condaFile = condaFile
        this.workspace = workspace
        this.targetImage = targetImage
        this.identity = identity
        this.platform = platform
        this.cacheRepository = cacheRepository
        this.startTime = Instant.now()
        this.ip = ip
        this.configJson = configJson
        this.offsetId = offsetId ?: OffsetDateTime.now().offset.id
        this.containerConfig = containerConfig
        this.scanId = scanId
        this.buildContext = buildContext
        this.format = format
        this.maxDuration = maxDuration
        this.compression = compression
        // NOTE: this is meant to be updated - automatically - when the request is submitted
        this.buildId = ID_PREFIX + containerId + SEP + '0'
    }

    BuildRequest(Map opts) {
        this.containerId = opts.containerId
        this.containerFile = opts.containerFile
        this.condaFile = opts.condaFile
        this.workspace = opts.workspace as Path
        this.targetImage = opts.targetImage
        this.identity = opts.identity as PlatformId
        this.platform = opts.platform as ContainerPlatform
        this.cacheRepository = opts.cacheRepository
        this.startTime = opts.startTime as Instant
        this.ip = opts.ip
        this.configJson = opts.configJson
        this.offsetId = opts.offesetId
        this.containerConfig = opts.containerConfig as ContainerConfig
        this.scanId = opts.scanId
        this.buildContext = opts.buildContext as BuildContext
        this.format = opts.format as BuildFormat
        this.maxDuration = opts.maxDuration as Duration
        this.compression = opts.compression as BuildCompression
        this.buildId = opts.buildId
    }

    @Override
    String toString() {
        return "BuildRequest[containerId=$containerId; targetImage=$targetImage; identity=$identity; dockerFile=${trunc(containerFile)}; condaFile=${trunc(condaFile)}; buildId=$buildId, maxDuration=$maxDuration]"
    }

    String getContainerId() {
        return containerId
    }

    @Deprecated
    String getDockerFile() {
        return containerFile
    }

    String getContainerFile() {
        return containerFile
    }

    String getCondaFile() {
        return condaFile
    }

    Path getWorkDir() {
        return workspace.resolve(buildId).toAbsolutePath()
    }

    String getTargetImage() {
        return targetImage
    }

    PlatformId getIdentity() {
        return identity
    }

    ContainerPlatform getPlatform() {
        return platform
    }

    String getCacheRepository() {
        return cacheRepository
    }

    Instant getStartTime() {
        return startTime
    }

    String getIp() {
        return ip
    }

    String getConfigJson() {
        return configJson
    }

    String getOffsetId() {
        return offsetId
    }

    Duration getMaxDuration() {
        return maxDuration
    }

    BuildCompression getCompression() {
        return compression
    }

    boolean formatDocker() {
        !format || format==DOCKER
    }

    boolean formatSingularity() {
        format==SINGULARITY
    }

    static String legacyBuildId(String id) {
        if( !id )
            return null
        return id.contains(SEP) ? id.tokenize(SEP)[0] : null
    }

}
