package io.seqera.wave.service.builder

import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime

import groovy.transform.EqualsAndHashCode
import groovy.transform.builder.Builder
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
import static io.seqera.wave.service.ContainerRequestServiceImpl.computeBuildChecksum
import static io.seqera.wave.service.ContainerRequestServiceImpl.computeCondaChecksum
import static io.seqera.wave.util.StringUtils.trunc
/**
 * Model a container builder result
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Builder(prefix='with')
@EqualsAndHashCode(includes = 'id,targetImage')
class BuildRequest {

    /**
     * Unique request Id. This is computed as a consistent hash generated from
     * the container build assets e.g. Dockerfile. Therefore the same container build
     * request should result in the same `id`
     */
    String id

    /**
     * The dockerfile content corresponding to this request
     */
    String dockerFile

    /**
     * The conda file recipe associated with this request
     */
    String condaFile

    /**
     * Conda file checksum
     */
    String condaId

    /**
     * The conda lock file associated with this request
     */
    String condaLock

    /**
     * The build context work directory
     */
    Path workDir

    /**
     * The target fully qualified image of the built container. It includes the target registry name
     */
    String targetImage

    /**
     * The (tower) user made this request
     */
    User user

    /**
     * Container platform
     */
    ContainerPlatform platform

    /**
     * Container repository for caching purposes
     */
    String cacheRepository

    /**
     * Build request start time
     */
    Instant startTime

    /**
     * Build job unique id
     */
    String jobId

    /**
     * The client IP if available
     */
    String requestIp

    /**
     * Docker config json holding repository authentication
     */
    String configJson

    /**
     * The time offset at the user timezone
     */
    String offsetId

    /* required by the builder annotation */
    protected BuildRequest() {}

    /**
     * Deprecated use {@code BuildRequest.builder().withSomething().build()} idiom instead
     */
    @Deprecated
    BuildRequest(String containerFile, Path workspace, String repo, String condaFile, User user, ContainerPlatform platform, String configJson, String cacheRepo, String ip, String offsetId = null) {
        this.id = computeBuildChecksum(containerFile, condaFile, platform, repo)
        this.dockerFile = containerFile
        this.condaFile = condaFile
        this.condaId = computeCondaChecksum(condaFile)
        this.targetImage = "${repo}:${id}"
        this.user = user
        this.platform = platform
        this.configJson = configJson
        this.cacheRepository = cacheRepo
        this.workDir = workspace.resolve(id).toAbsolutePath()
        this.offsetId = offsetId ?: OffsetDateTime.now().offset.id
        this.startTime = Instant.now()
        this.jobId = "${id}-${startTime.toEpochMilli().toString().md5()[-5..-1]}"
        this.requestIp = ip
    }

    @Override
    String toString() {
        return "BuildRequest[id=$id; targetImage=$targetImage; user=$user; dockerFile=${trunc(dockerFile)}; condaFile=${trunc(condaFile)}]"
    }

}
