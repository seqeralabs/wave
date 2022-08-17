package io.seqera.wave.service.builder

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.config.WaveConfiguration
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.mail.MailService
import io.seqera.wave.tower.User
import io.seqera.wave.util.ThreadPoolBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.StringUtils.indent
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

    @Inject
    private WaveConfiguration configuration

    @Inject
    @Nullable
    private MailService mailService

    private final Map<String,BuildRequest> buildRequests = new HashMap<>()

    private ExecutorService executor

    @Inject
    private RegistryLookupService lookupService

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    @Inject
    private BuildStrategy buildStrategy

    @PostConstruct
    void init() {
        executor = ThreadPoolBuilder.io(10, 10, 100, 'wave-builder')
    }

    /**
     * Build a container image for the given dockerfile and conda files
     *
     * @param dockerfileContent
     *      The dockerfile text content to build the container
     * @param condaFile
     *      A Conda recipe file that may be used to build the container (optional)
     * @param user
     *      Tower user identifier that submitted the request
     * @return
     *      A fully qualified container repository where the built container is made available
     */
    @Override
    String buildImage(String dockerfileContent, @Nullable String condaFile, @Nullable User user) {
        if( !dockerfileContent )
            throw new BadRequestException("Missing dockerfile content")
        // create a unique digest to identify the request
        final request = new BuildRequest(dockerfileContent, Path.of(configuration.build.workspace), configuration.build.repo, condaFile, user)
        return getOrSubmit(request)
    }

    /**
     * Get a completable future that holds the build result
     *
     * @param targetImage
     *      the container repository name where the target image is expected to be retrieved once the
     *      build it complete
     * @return
     *      A completable future that holds the resulting {@link BuildResult} or
     *      {@code null} if not request has been submitted for such image
     */
    @Override
    CompletableFuture<BuildResult> buildResult(String targetImage) {
        return buildRequests.get(targetImage)?.result
    }

    protected String credsJson(String registry) {
        final info = lookupService.lookup(registry)
        final creds = credentialsProvider.getCredentials(registry)
        if( !creds ) {
            return null
        }
        final encode = "${creds.username}:${creds.password}".getBytes().encodeBase64()
        return """{"auths":{"${info.getHost()}":{"auth":"$encode"}}}"""
    }

    protected BuildResult launch(BuildRequest req) {
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
        final creds = credsJson(configuration.build.repo)

        // launch an external process to build the container
        try {
            final resp = buildStrategy.build(req, creds)
            log.info "== Build completed with status=$resp.exitStatus; stdout: (see below)\n${indent(resp.logs)}"
            return resp
        }
        catch (Exception e) {
            log.error "== Ouch! Unable to build container request=$req", e
            return new BuildResult(req.id, -1, e.message, req.startTime)
        }
        finally {
            if( !configuration.build.debug )
                buildStrategy.cleanup(req)
        }
    }

    protected CompletableFuture<BuildResult> launchAsync(BuildRequest request) {
        CompletableFuture
                .<BuildResult>supplyAsync(() -> launch(request), executor)
                .thenApply((result) -> { sendCompletionEmail(request,result); return result })
    }

    protected sendCompletionEmail(BuildRequest request, BuildResult result) {
        try {
            mailService?.sendCompletionMail(request, result)
        }
        catch (Exception e) {
            log.warn "Enable to send completion notication - reason: ${e.message?:e}"
        }
    }

    protected String getOrSubmit(BuildRequest request) {
        // use the target image as the cache key
        synchronized (buildRequests) {
            if( !buildRequests.containsKey(request.targetImage) ) {
                log.info "== Submit build request request: $request"
                request.result = launchAsync(request)
                buildRequests.put(request.targetImage, request)
            }
            else {
                log.info "== Hit build cache for request: $request"
            }
            return request.targetImage
        }
    }

}
