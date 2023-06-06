package io.seqera.wave.service.builder

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.core.ContainerPlatform
import jakarta.inject.Singleton

import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.WRITE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING

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

    @Value('${wave.build.timeout}')
    Duration buildTimeout

    @Value('${wave.debug}')
    Boolean debug

    @Override
    BuildResult build(BuildRequest req) {

        Path configFile = null
        if( req.configJson ) {
            configFile = req.workDir.resolve('config.json')
            Files.write(configFile, JsonOutput.prettyPrint(req.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }

        // command the docker build command
        final buildCmd= buildCmd(req, configFile)
        log.debug "Build run command: ${buildCmd.join(' ')}"
        // save docker cli for debugging purpose
        if( debug ) {
            Files.write(req.workDir.resolve('docker.sh'),
                    buildCmd.join(' ').bytes,
                    CREATE, WRITE, TRUNCATE_EXISTING)
        }
        
        final proc = new ProcessBuilder()
                .command(buildCmd)
                .directory(req.workDir.toFile())
                .redirectErrorStream(true)
                .start()

        final completed = proc.waitFor(buildTimeout.toSeconds(), TimeUnit.SECONDS)
        final stdout = proc.inputStream.text
        return BuildResult.completed(req.id, completed ? proc.exitValue() : -1, stdout, req.startTime)
    }

    protected List<String> buildCmd(BuildRequest req, Path credsFile) {
        final dockerCmd = dockerWrapper(req.workDir, credsFile, spackCacheDir(req), req.platform)
        return dockerCmd + launchCmd(req)
    }

    protected List<String> dockerWrapper(Path workDir, Path credsFile, Path spackCacheDir, ContainerPlatform platform ) {
        final wrapper = ['docker',
                         'run',
                         '--rm',
                         '-w', workDir.toString(),
                         '-v', "$workDir:$workDir".toString()]

        if( credsFile ) {
            wrapper.add('-v')
            wrapper.add("$credsFile:/kaniko/.docker/config.json:ro".toString())
        }

        if( spackCacheDir ) {
            wrapper.add('-v')
            wrapper.add("$spackCacheDir:/opt/spack/cache:rw".toString())
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
