package io.seqera.wave.service.builder

import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
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
     * The dockerfile content corresponding to this request
     */
    final String dockerFile

    /**
     * The conda file recipe associated with this request
     */
    final String condaFile

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

    BuildRequest(String containerFile, Path workspace, String repo, String condaFile, User user, ContainerPlatform platform, String configJson, String cacheRepo, String ip, String offsetId = null) {
        this.id = computeDigest(containerFile, condaFile, platform, repo)
        this.dockerFile = containerFile
        this.condaFile = condaFile
        this.targetImage = "${repo}:${id}"
        this.user = user
        this.platform = platform
        this.configJson = configJson
        this.cacheRepository = cacheRepo
        this.workDir = workspace.resolve(id).toAbsolutePath()
        this.offsetId = offsetId ?: OffsetDateTime.now().offset.id
        this.startTime = Instant.now()
        this.job = "${id}-${startTime.toEpochMilli().toString().md5()[-5..-1]}"
        this.ip = ip
    }

    static private String computeDigest(String containerFile, String condaFile, ContainerPlatform platform, String repository) {
        final attrs = new LinkedHashMap<String,Object>(10)
        attrs.containerFile = containerFile
        attrs.condaFile = condaFile
        attrs.platform =  platform.toString()
        attrs.repository = repository
        return DigestFunctions.md5(attrs)
    }

    @Override
    String toString() {
        return "BuildRequest[id=$id; targetImage=$targetImage; user=$user; dockerFile=${trunc(dockerFile)}; condaFile=${trunc(condaFile)}]"
    }

}
