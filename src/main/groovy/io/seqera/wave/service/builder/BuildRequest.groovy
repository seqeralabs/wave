package io.seqera.wave.service.builder

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Future

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
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

    final String id
    final String dockerFile
    final String condaFile
    final Path workDir
    final String targetImage
    final User user
    final Instant startTime
    volatile Future<BuildResult> result

    BuildRequest(String dockerFile, Path workspace, String repo, String condaFile, User user) {
        final content = condaFile ? dockerFile + condaFile : dockerFile
        this.id = DigestFunctions.md5(content)
        this.dockerFile = dockerFile
        this.condaFile = condaFile
        this.targetImage = "${repo}:${id}"
        this.user = user
        this.workDir = workspace.resolve(id).toAbsolutePath()
        this.startTime = Instant.now()
    }

    @Override
    String toString() {
        return "BuildRequest[id=$id; targetImage=$targetImage; user=$user; dockerFile=${trunc(dockerFile)}; condaFile=${trunc(condaFile)}]"
    }

}
