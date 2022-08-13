package io.seqera.wave.service.builder

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Future

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.seqera.wave.tower.User
import io.seqera.wave.util.DigestFunctions
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

    private String trunc(String value) {
        if( !value ) return value
        final lines = value.readLines()
        return lines.size()==1 ? lines[0] : lines[0] + ' (more omitted)'
    }
}
