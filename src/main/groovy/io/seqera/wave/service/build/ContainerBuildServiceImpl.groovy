package io.seqera.wave.service.build

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.util.ThreadPoolBuilder
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.APPEND
import static java.nio.file.StandardOpenOption.CREATE
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerBuildServiceImpl implements ContainerBuildService {

    /**
     * File system path there the dockerfile is save
     */
    @Value('${wave.build.workspace}')
    String workspace

    @Value('${wave.debug}')
    Boolean debugMode

    /**
     * The registry repository where the build image will be stored
     */
    @Value('${wave.build.repository}')
    String repository

    @Value('${wave.build.dockerMode:false}')
    Boolean dockerMode

    @Value('${wave.build.image}')
    String buildImage

    @Value('${wave.build.timeout:1m}')
    Duration buildTimeout

    private final Map<String,BuildRequest> buildRequests = new HashMap<>()

    private ExecutorService executor

    @PostConstruct
    void init() {
        executor = ThreadPoolBuilder.io(10, 10, 100, 'wave-builder')
    }

    @Override
    String buildImage(String dockerfileContent) {
        if( !dockerfileContent )
            throw new BadRequestException("Missing dockerfile content")
        return getOrSubmit(dockerfileContent)
    }

    @Override
    BuildStatus waitImageBuild(String targetImage) {
        final req = buildRequests.get(targetImage)
        if( !req )
            return BuildStatus.UNKNOWN
        final future = req.result
        if( future.isCancelled() )
            return BuildStatus.FAILED
        if( future.isDone() )
            return ret(req.result.get())
        final begin = System.currentTimeMillis()
        while( true ) {
            try {
                return ret(future.get(1, TimeUnit.SECONDS))
            }
            catch (TimeoutException e) {
                final delta = System.currentTimeMillis() - begin
                if( delta > buildTimeout.toMillis() ) {
                    log.debug "Build timeout for image: $targetImage"
                    return BuildStatus.FAILED
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                log.error "Build failed for image: $targetImage -- cause: ${e.message}", e
                return BuildStatus.FAILED
            }
        }
    }

    protected BuildStatus ret(BuildResult result) {
        result.exitStatus==0 ? BuildStatus.SUCCEED : BuildStatus.FAILED
    }

    protected List<String> dockerWrapper(Path workDir) {
        ['docker',
         'run',
         '--rm',
         '-w', workDir.toString(),
         '-v', "$workDir:$workDir".toString(),
         '-e', 'AWS_ACCESS_KEY_ID',
         '-e', 'AWS_SECRET_ACCESS_KEY',
         buildImage]
    }

    protected List<String> launchCmd(BuildRequest req, boolean dockerMode) {
        final result = dockerMode
                ? dockerWrapper(req.workDir)
                : new ArrayList()
        result
                << "/kaniko/executor"
                << "--dockerfile"
                << "$req.workDir/Dockerfile".toString()
                << "--destination"
                << req.targetImage
    }

    protected BuildResult launch(BuildRequest req, boolean dockerMode) {
        // create the workdir path
        Files.createDirectories(req.workDir)
        // save the dockerfile
        final dockerfile = req.workDir.resolve('Dockerfile')
        Files.write(dockerfile, req.dockerfile.bytes, CREATE, APPEND)

        try {
            final cmd = launchCmd(req, dockerMode)
            log.debug "Build run command: ${cmd.join(' ')}"
            final proc = new ProcessBuilder()
                    .command(cmd)
                    .directory(req.workDir.toFile())
                    .start()

            final failed = proc.waitFor()
            final err = proc.errorStream.text
            log.debug "Build command: ${cmd.join(' ')}\n- completed with status=$failed\n- error: ${err}"
            return new BuildResult(failed, err)
        }
        finally {
            if( !debugMode )
                dockerfile.parent.deleteDir()
        }
    }

    protected Callable<BuildResult> callLaunch(BuildRequest request) {
        new Callable<BuildResult>() {
            @Override
            BuildResult call() throws Exception {
                return launch(request, dockerMode)
            }
        }
    }

    protected String getOrSubmit(String dockerfile) {
        // create a unique digest to identify the request
        final request = new BuildRequest(dockerfile, Path.of(workspace), repository)
        // use the target image as the cache key
        synchronized (buildRequests) {
            if( !buildRequests.containsKey(request.targetImage) ) {
                log.debug "Submit build request request: $request"
                request.result = executor.submit(callLaunch(request))
                buildRequests.put(request.targetImage, request)
            }
            else {
                log.debug "Hit build cache for request: $request"
            }
            return request.targetImage
        }
    }

}
