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
import java.time.Instant
import java.time.OffsetDateTime

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.RegHelper
import static io.seqera.wave.service.builder.BuildFormat.DOCKER
import static io.seqera.wave.service.builder.BuildFormat.SINGULARITY
import static io.seqera.wave.util.RegHelper.guessCondaRecipeName
import static io.seqera.wave.util.RegHelper.guessSpackRecipeName
import static io.seqera.wave.util.StringUtils.trunc
/**
 * Model a container builder result
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode(includes = 'containerId,targetImage,buildId')
@CompileStatic
class BuildRequest {

    static final String SEP = '_'

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
     * The spock file recipe associated with this request
     */
    final String spackFile

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
     * Whenever is a spack build
     */
    final boolean isSpackBuild

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
    
    volatile String buildId

    volatile Path workDir

    BuildRequest(String containerFile, Path workspace, String repo, String condaFile, String spackFile, BuildFormat format, PlatformId identity, ContainerConfig containerConfig, BuildContext buildContext, ContainerPlatform platform, String configJson, String cacheRepo, String scanId, String ip, String offsetId) {
        this.containerId = computeDigest(containerFile, condaFile, spackFile, platform, repo, buildContext)
        this.containerFile = containerFile
        this.containerConfig = containerConfig
        this.buildContext = buildContext
        this.condaFile = condaFile
        this.spackFile = spackFile
        this.targetImage = makeTarget(format, repo, containerId, condaFile, spackFile)
        this.format = format
        this.identity = identity
        this.platform = platform
        this.configJson = configJson
        this.cacheRepository = cacheRepo
        this.workspace = workspace
        this.offsetId = offsetId ?: OffsetDateTime.now().offset.id
        this.startTime = Instant.now()
        this.ip = ip
        this.isSpackBuild = spackFile
        this.scanId = scanId
    }

    BuildRequest(Map opts) {
        this.containerId = opts.id
        this.containerFile = opts.containerFile
        this.condaFile = opts.condaFile
        this.spackFile = opts.spackFile
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
        this.isSpackBuild = opts.isSpackBuild
        this.scanId = opts.scanId
        this.buildContext = opts.buildContext as BuildContext
        this.format = opts.format as BuildFormat
        this.workDir = opts.workDir as Path
        this.buildId = opts.buildId
    }

    static protected String makeTarget(BuildFormat format, String repo, String id, @Nullable String condaFile, @Nullable String spackFile) {
        assert id, "Argument 'id' cannot be null or empty"
        assert repo, "Argument 'repo' cannot be null or empty"
        assert format, "Argument 'format' cannot be null"

        String prefix
        def tag = id
        if( condaFile && (prefix=guessCondaRecipeName(condaFile)) ) {
            tag = "${normaliseTag(prefix)}--${id}"
        }
        else if( spackFile && (prefix=guessSpackRecipeName(spackFile)) ) {
            tag = "${normaliseTag(prefix)}--${id}"
        }

        format==SINGULARITY ? "oras://${repo}:${tag}" : "${repo}:${tag}"
    }

    static protected String normaliseTag(String tag, int maxLength=80) {
        assert maxLength>0, "Argument maxLength cannot be less or equals to zero"
        if( !tag )
            return null
        // docker tag only allows [a-z0-9.-_]
        tag = tag.replaceAll(/[^a-zA-Z0-9_.-]/,'')
        // only allow max 100 chars
        if( tag.length()>maxLength ) {
            // try to tokenize splitting by `_`
            def result = ''
            def parts = tag.tokenize('_')
            for( String it : parts ) {
                if( result )
                    result += '_'
                result += it
                if( result.size()>maxLength )
                    break
            }
            tag = result
        }
        // still too long, trunc it
        if( tag.length()>maxLength ) {
            tag = tag.substring(0,maxLength)
        }
        // remove trailing or leading special chars
        tag = tag.replaceAll(/^(\W|_)+|(\W|_)+$/,'')
        return tag ?: null
    }

    static private String computeDigest(String containerFile, String condaFile, String spackFile, ContainerPlatform platform, String repository, BuildContext buildContext) {
        final attrs = new LinkedHashMap<String,String>(10)
        attrs.containerFile = containerFile
        attrs.condaFile = condaFile
        attrs.platform = platform?.toString()
        attrs.repository = repository
        if( spackFile ) attrs.spackFile = spackFile
        if( buildContext ) attrs.buildContext = buildContext.tarDigest
        return RegHelper.sipHash(attrs)
    }

    @Override
    String toString() {
        return "BuildRequest[containerId=$containerId; targetImage=$targetImage; identity=$identity; dockerFile=${trunc(containerFile)}; condaFile=${trunc(condaFile)}; spackFile=${trunc(spackFile)}; buildId=$buildId]"
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

    String getSpackFile() {
        return spackFile
    }

    Path getWorkDir() {
        return workDir
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

    boolean formatDocker() {
        !format || format==DOCKER
    }

    boolean formatSingularity() {
        format==SINGULARITY
    }

    BuildRequest withBuildId(String id) {
        this.buildId = containerId + SEP + id
        this.workDir = workspace.resolve(buildId).toAbsolutePath()
        return this
    }

    static String legacyBuildId(String id) {
        if( !id )
            return null
        return id.contains(SEP) ? id.tokenize(SEP)[0] : null
    }

}
