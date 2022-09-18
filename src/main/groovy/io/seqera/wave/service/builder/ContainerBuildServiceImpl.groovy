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
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE
/**
 * Implements container build service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerBuildServiceImpl implements ContainerBuildService {

    @Value('${wave.debug}')
    @Nullable
    Boolean debugMode

    @Value('${wave.build.image}')
    String buildImage

    @Value('${wave.build.timeout:5m}')
    Duration buildTimeout

    @Inject
    @Nullable
    private MailService mailService

    @Inject
    private BuildStore buildRequests

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
        return buildRequests
                .awaitBuild(targetImage)
    }

    protected String credsJson(Set<String> repositories, Long userId, Long workspaceId) {
        final result = new StringBuilder()
        for( String repo : repositories ) {
            final path = ContainerCoordinates.parse(repo)
            final info = lookupService.lookup(path.registry)
            final creds = credentialsProvider.getUserCredentials(path, userId, workspaceId)
            log.debug "Build credentials for repository: $repo => $creds"
            if( !creds )
                continue
            final encode = "${creds.username}:${creds.password}".getBytes().encodeBase64()
            if( result.size() )
                result.append(',')
            result.append("\"${info.index}\":{\"auth\":\"$encode\"}")
        }
        return result.size() ? """{"auths":{$result}}""" : null
    }

    static protected Set<String> findRepositories(String dockerfile) {
        final result = new HashSet()
        if( !dockerfile )
            return result
        for( String line : dockerfile.readLines()) {
            if( !line.trim().toLowerCase().startsWith('from '))
                continue
            def repo = line.trim().substring(5)
            def p = repo.indexOf(' ')
            if( p!=-1 )
                repo = repo.substring(0,p)
            result.add(repo)
        }
        return result
    }

    protected BuildResult launch(BuildRequest req) {
        // launch an external process to build the container
        BuildResult resp=null
        try {
            // create the workdir path
            Files.createDirectories(req.workDir)
            // save the dockerfile
            final dockerfile = req.workDir.resolve('Dockerfile')
            Files.write(dockerfile, req.dockerFile.bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            // save the conda file
            if( req.condaFile ) {
                final condaFile = req.workDir.resolve('conda.yml')
                Files.write(condaFile, req.condaFile.bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }
            // find repos
            final repos = findRepositories(req.dockerFile) + req.targetImage + req.cacheRepository
            // create creds file for target repo
            final creds = credsJson(repos, req.user?.id, req.workspaceId)

            resp = buildStrategy.build(req, creds)
            log.info "== Build completed with status=$resp.exitStatus; stdout: (see below)\n${indent(resp.logs)}"
            return resp
        }
        catch (Throwable e) {
            log.error "== Ouch! Unable to build container req=$req", e
            return resp = BuildResult.failed(req.id, e.message, req.startTime)
        }
        finally {
            // update build status store
            buildRequests.storeBuild(req.targetImage, resp)
            // cleanup build context
            if( !debugMode )
                buildStrategy.cleanup(req)
        }
    }

    protected CompletableFuture<BuildResult> launchAsync(BuildRequest request) {

        if( rateLimiterService )
            rateLimiterService.acquireBuild(new AcquireRequest(request.user?.id?.toString(), request.ip))

        buildRequests.storeBuild(request.targetImage, BuildResult.create(request))

        CompletableFuture
                .<BuildResult>supplyAsync(() -> launch(request), executor)
                .thenApply((result) -> { sendCompletionEmail(request,result); return result })
    }

    protected sendCompletionEmail(BuildRequest request, BuildResult result) {
        try {
            mailService?.sendCompletionEmail(request, result)
        }
        catch (Exception e) {
            log.warn "Unable to send completion notication - reason: ${e.message?:e}"
        }
    }

    protected String getOrSubmit(BuildRequest request) {
        // use the target image as the cache key
        synchronized (buildRequests) {
            if( !buildRequests.hasBuild(request.targetImage) ) {
                log.info "== Submit build request request: $request"
                launchAsync(request)
            }
            else {
                log.info "== Hit build cache for request: $request"
            }
            return request.targetImage
        }
    }

}
