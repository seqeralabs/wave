package io.seqera.wave.service.builder

import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
import io.seqera.wave.util.DigestFunctions
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
        this.targetImage = "${repo}:${id}"
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

    static private String computeDigest(String containerFile, String condaFile, String spackFile, ContainerPlatform platform, String repository, BuildContext buildContext) {
        final attrs = new LinkedHashMap<String,Object>(10)
        attrs.containerFile = containerFile
        attrs.condaFile = condaFile
        attrs.platform = platform
        attrs.repository = repository
        if( spackFile ) attrs.spackFile = spackFile
        if( buildContext ) attrs.buildContext = buildContext.tarDigest
        return DigestFunctions.md5(attrs)
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
}
