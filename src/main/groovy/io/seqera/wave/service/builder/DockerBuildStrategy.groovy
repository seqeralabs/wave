package io.seqera.wave.service.builder

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.core.ContainerPlatform
import jakarta.inject.Singleton
/**
 *  Build a container image using a Docker CLI tool
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class DockerBuildStrategy extends BuildStrategy {

    @Value('${wave.build.image}')
    String buildImage

    @Value('${wave.build.timeout:5m}')
    Duration buildTimeout

    @Override
    BuildResult build(BuildRequest req, String creds) {

        Path credsFile = null
        if( creds ) {
            credsFile = req.workDir.resolve('config.json')
            Files.write(credsFile, creds.bytes)
        }

        // comand the docker build command
        final buildCmd= buildCmd(req, credsFile)
        log.debug "Build run command: ${buildCmd.join(' ')}"
        final proc = new ProcessBuilder()
                .command(buildCmd)
                .directory(req.workDir.toFile())
                .redirectErrorStream(true)
                .start()

        final completed = proc.waitFor(buildTimeout.toSeconds(), TimeUnit.SECONDS)
        final stdout = proc.inputStream.text
        return new BuildResult(req.id, completed ? proc.exitValue() : -1, stdout, req.startTime)

    }

    protected List<String> buildCmd(BuildRequest req, Path credsFile) {
        final dockerCmd = dockerWrapper(req.workDir, credsFile, req.platform)
        return dockerCmd + launchCmd(req)
    }

    protected List<String> dockerWrapper(Path workDir, Path credsFile, ContainerPlatform platform) {
        final wrapper = ['docker',
                         'run',
                         '--rm',
                         '-w', workDir.toString(),
                         '-v', "$workDir:$workDir".toString()]

        if( credsFile ) {
            wrapper.add('-v')
            wrapper.add("$credsFile:/kaniko/.docker/config.json:ro".toString())
        }

        if( platform ) {
            wrapper.add('--platform')
            wrapper.add(platform.toString())
        }
        // the container image to be used t
        wrapper.add( buildImage )
        // return it
        return wrapper
    }

}
