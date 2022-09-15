package io.seqera.wave.service.builder

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.seqera.wave.core.ContainerPlatform
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
     * Local workspace used to build the image
     */
    final String workspace

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
    final Long userId

    /**
     * The email of the user
     */
    final String email

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
    final String startTimeStr

    Instant getStartTime(){
        Instant.parse(startTimeStr)
    }

    /**
     * Build jon unique id
     */
    final String job

    /**
     * Reference to the future build job result
     */
    volatile BuildResult result

    /**
     * The client IP if available
     */
    final String ip;

    @JsonCreator
    BuildRequest(
            @JsonProperty("dockerFile")String dockerFile,
            @JsonProperty("workspace")String workspace,
            @JsonProperty("repo")String repo,
            @JsonProperty("condaFile")String condaFile,
            @JsonProperty("userId")Long userId,
            @JsonProperty("email")String email,
            @JsonProperty("platform")ContainerPlatform platform,
            @JsonProperty("cacheRepo")String cacheRepo,
            @JsonProperty("ip")String ip) {
        this.id = computeDigest(dockerFile,condaFile,platform)
        this.workspace = workspace
        this.dockerFile = dockerFile
        this.condaFile = condaFile
        this.targetImage = "${repo}:${id}"
        this.userId = userId
        this.email = email
        this.platform = platform
        this.cacheRepository = cacheRepo
        this.ip = ip
        this.workDir = workspace ? Path.of(workspace).resolve(id).toAbsolutePath() : null
        this.startTimeStr = Instant.now().toString()
        this.job = "${id}-${startTimeStr.md5()[-5..-1]}"
    }

    boolean isFinished(){
        this.result?.duration != null
    }

    static private String computeDigest(String dockerFile, String condaFile, ContainerPlatform platform) {
        def content = platform?.toString() ?:""
        content += dockerFile
        if( condaFile )
            content += condaFile
        return DigestFunctions.md5(content)
    }

    @Override
    String toString() {
        return "BuildRequest[id=$id; targetImage=$targetImage; user=$userId; dockerFile=${trunc(dockerFile)}; condaFile=${trunc(condaFile)}]"
    }

}
