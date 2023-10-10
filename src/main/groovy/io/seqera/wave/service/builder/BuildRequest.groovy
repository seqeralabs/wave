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

package io.seqera.wave.service.builder

import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import io.micronaut.core.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
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
@EqualsAndHashCode(includes = 'id,targetImage')
@CompileStatic
class BuildRequest {

    /**
     * Unique request Id. This is computed as a consistent hash generated from
     * the container build assets e.g. Dockerfile. Therefore the same container build
     * request should result in the same `id`
     */
    final String id

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
    final Path workDir

    /**
     * The target fully qualified image of the built container. It includes the target registry name
     */
    final String targetImage

    /**
     * The (tower) user made this request
     */
    final User user

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
     * Build job unique id
     */
    final String job

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
    
    /**
     * Mark this request as not cached
     */
    volatile boolean uncached

    BuildRequest(String containerFile, Path workspace, String repo, String condaFile, String spackFile, BuildFormat format, User user, ContainerConfig containerConfig, BuildContext buildContext, ContainerPlatform platform, String configJson, String cacheRepo, String scanId, String ip, String offsetId) {
        this.id = computeDigest(containerFile, condaFile, spackFile, platform, repo, buildContext)
        this.containerFile = containerFile
        this.containerConfig = containerConfig
        this.buildContext = buildContext
        this.condaFile = condaFile
        this.spackFile = spackFile
        this.targetImage = makeTarget(format, repo, id, condaFile, spackFile)
        this.format = format
        this.user = user
        this.platform = platform
        this.configJson = configJson
        this.cacheRepository = cacheRepo
        this.workDir = workspace.resolve(id).toAbsolutePath()
        this.offsetId = offsetId ?: OffsetDateTime.now().offset.id
        this.startTime = Instant.now()
        this.job = "${id}-${startTime.toEpochMilli().toString().md5()[-5..-1]}"
        this.ip = ip
        this.isSpackBuild = spackFile
        this.scanId = scanId
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
        return "BuildRequest[id=$id; targetImage=$targetImage; user=$user; dockerFile=${trunc(containerFile)}; condaFile=${trunc(condaFile)}; spackFile=${trunc(spackFile)}]"
    }

    String getId() {
        return id
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

    User getUser() {
        return user
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

    String getJob() {
        return job
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
}
