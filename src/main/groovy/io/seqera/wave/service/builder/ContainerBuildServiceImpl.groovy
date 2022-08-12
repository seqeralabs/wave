package io.seqera.wave.service.builder

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.mail.MailService
import io.seqera.wave.tower.User
import io.seqera.wave.util.ThreadPoolBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.APPEND
import static java.nio.file.StandardOpenOption.CREATE
/**
 * Implements container build service
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
    @Value('${wave.build.repo}')
    String buildRepo

    @Value('${wave.build.cache}')
    String cacheRepo

    @Value('${wave.build.dockerMode:false}')
    Boolean dockerMode

    @Value('${wave.build.image}')
    String buildImage

    @Value('${wave.build.timeout:5m}')
    Duration buildTimeout

    @Inject
    @Nullable
    private MailService mailService

    private final Map<String,BuildRequest> buildRequests = new HashMap<>()

    private ExecutorService executor

    @Inject
    private RegistryLookupService lookupService

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    @PostConstruct
    void init() {
        executor = ThreadPoolBuilder.io(10, 10, 100, 'wave-builder')
    }

    @Override
    String buildImage(String dockerfileContent, String condaFile, User user) {
        if( !dockerfileContent )
            throw new BadRequestException("Missing dockerfile content")
        // create a unique digest to identify the request
        final request = new BuildRequest(dockerfileContent, Path.of(workspace), buildRepo, condaFile, user)
        return getOrSubmit(request)
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
                    log.info "== Build timeout for image: $targetImage"
                    return BuildStatus.FAILED
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            catch (Exception e) {
                log.error "== Build failed for image: $targetImage -- cause: ${e.message}", e
                return BuildStatus.FAILED
            }
        }
    }

    protected BuildStatus ret(BuildResult result) {
        result.exitStatus==0 ? BuildStatus.SUCCEED : BuildStatus.FAILED
    }

    protected List<String> dockerWrapper(Path workDir, Path credsFile) {
        final wrapper = ['docker',
                 'run',
                 '--rm',
                 '-w', workDir.toString(),
                 '-v', "$workDir:$workDir".toString()]

        if( credsFile ) {
            wrapper.add('-v')
            wrapper.add("$credsFile:/kaniko/.docker/config.json:ro".toString())
        }
        // the container image to be used t
        wrapper.add( buildImage )
        // return it
        return wrapper
    }

    protected String credsJson(String registry) {
        final info = lookupService.lookup(registry)
        final creds = credentialsProvider.getCredentials(registry)
        if( !creds ) {
            return null
        }
        final encode = "${creds.username}:${creds.password}".getBytes().encodeBase64()
        return """\
        {
            "auths": {
                "${info.getHost()}": {
                    "auth": "$encode"
                }
            }
        }
        """.stripIndent()
    }

    protected List<String> launchCmd(BuildRequest req, boolean dockerMode, Path credsFile) {
        final result = dockerMode
                ? dockerWrapper(req.workDir, credsFile)
                : new ArrayList()
        result
                << "/kaniko/executor"
                << "--dockerfile"
                << "$req.workDir/Dockerfile".toString()
                << '--context'
                << req.workDir.toString()
                << "--destination"
                << req.targetImage
                << "--cache=true"
                << "--cache-repo"
                << cacheRepo
    }

    protected BuildResult launch(BuildRequest req, boolean dockerMode) {
        // create the workdir path
        Files.createDirectories(req.workDir)
        // save the dockerfile
        final dockerfile = req.workDir.resolve('Dockerfile')
        Files.write(dockerfile, req.dockerFile.bytes, CREATE, APPEND)
        // save the conda file
        if( req.condaFile ) {
            final condaFile = req.workDir.resolve('conda.yml')
            Files.write(condaFile, req.condaFile.bytes, CREATE, APPEND)
        }
        // create creds file for target repo
        Path credsFile = req.workDir.resolve('config.json')
        final creds = credsJson(buildRepo)
        if( creds ) {
            Files.write(credsFile, creds.bytes)
        }
        else
            credsFile = null

        // launch an external process to build the container
        try {
            final cmd = launchCmd(req, dockerMode, credsFile)
            log.debug "Build run command: ${cmd.join(' ')}"
            final proc = new ProcessBuilder()
                    .command(cmd)
                    .directory(req.workDir.toFile())
                    .redirectErrorStream(true)
                    .start()

            final failed = proc.waitFor()
            final stdout = proc.inputStream.text
            log.info "== Build completed: ${cmd.join(' ')}\n- completed with status=$failed\n- stdout: ${stdout}"
            return new BuildResult(req.id, failed, stdout, req.startTime)
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
                final result = launch(request, dockerMode)
                mailService?.sendCompletionMail(result, request.user)
                return result
            }
        }
    }

    protected String getOrSubmit(BuildRequest request) {
        // use the target image as the cache key
        synchronized (buildRequests) {
            if( !buildRequests.containsKey(request.targetImage) ) {
                log.info "== Submit build request request: $request"
                request.result = executor.submit(callLaunch(request))
                buildRequests.put(request.targetImage, request)
            }
            else {
                log.info "== Hit build cache for request: $request"
            }
            return request.targetImage
        }
    }

}
