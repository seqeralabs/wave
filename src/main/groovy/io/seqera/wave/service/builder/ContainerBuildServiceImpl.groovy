package io.seqera.wave.service.builder

import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.WaveDefault
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.RateLimiterService
import io.seqera.wave.service.mail.MailService
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

    @Value('${wave.build.debug}')
    @Nullable
    Boolean debugMode

    /**
     * The registry repository where the build image will be stored
     */
    @Value('${wave.build.repo}')
    String buildRepo

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

    @Inject
    private BuildStrategy buildStrategy

    @Inject
    @Nullable
    private RateLimiterService rateLimiterService

    @PostConstruct
    void init() {
        executor = ThreadPoolBuilder.io(10, 10, 100, 'wave-builder')
    }

    /**
     * Build a container image for the given {@link BuildRequest}
     *
     * @param request
     *      A {@link BuildRequest} modelling the build request
     * @return
     *      The container image where the resulting image is going to be hosted
     */
    @Override
    String buildImage(BuildRequest request) {
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

    protected String credsJson(Set<String> registries) {
        final result = new StringBuilder()
        for( String reg : registries ) {
            final info = lookupService.lookup(reg)
            final creds = credentialsProvider.getCredentials(reg)
            if( !creds ) {
                return null
            }
            final encode = "${creds.username}:${creds.password}".getBytes().encodeBase64()
            if( result.size() )
                result.append(',')
            result.append("\"${info.getHost()}\":{\"auth\":\"$encode\"}")
        }
        return """{"auths":{$result}}"""
    }

    static protected Set<String> findRepositories(String dockerfile) {
        final result = new HashSet()
        if( !dockerfile )
            return result
        for( String line : dockerfile.readLines()) {
            if( !line.trim().toLowerCase().startsWith('from '))
                continue
            final cords = ContainerCoordinates.parse(line.trim().substring(5))
            final reg = cords.registry ?: WaveDefault.DOCKER_IO
            result.add(reg)
        }
        return result
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
        // find repos
        final repos = findRepositories(req.dockerFile) + buildRepo
        
        // create creds file for target repo
        final creds = credsJson(repos)

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
            if( !debugMode )
                buildStrategy.cleanup(req)
        }
    }

    protected CompletableFuture<BuildResult> launchAsync(BuildRequest request) {

        rateLimiterService?.acquireBuild(new AcquireRequest(request.user?.id?.toString(),request.ip))

        CompletableFuture
                .<BuildResult>supplyAsync(() -> launch(request), executor)
                .thenApply((result) -> { sendCompletionEmail(request,result); return result })
    }

    protected sendCompletionEmail(BuildRequest request, BuildResult result) {
        try {
            mailService?.sendCompletionMail(request, result)
        }
        catch (Exception e) {
            log.warn "Unable to send completion notication - reason: ${e.message?:e}"
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
