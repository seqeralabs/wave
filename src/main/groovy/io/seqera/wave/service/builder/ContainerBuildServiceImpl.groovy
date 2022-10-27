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
import io.micronaut.context.event.ApplicationEventPublisher
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.RateLimiterService
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

    @Value('${wave.build.status.duration:`1d`}')
    private Duration statusDuration

    @Value('${wave.build.status.delay:5s}')
    private Duration statusDelay

    @Value('${wave.build.cleanup}')
    @Nullable
    String cleanup

    @Inject
    ApplicationEventPublisher<BuildEvent> eventPublisher

    @Inject
    private BuildStore buildStore

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
    void buildImage(BuildRequest request) {
        checkOrSubmit(request)
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
        return buildStore
                .awaitBuild(targetImage)
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

            resp = buildStrategy.build(req)
            log.info "== Build completed with status=$resp.exitStatus; stdout: (see below)\n${indent(resp.logs)}"
            return resp
        }
        catch (Throwable e) {
            log.error "== Ouch! Unable to build container req=$req", e
            return resp = BuildResult.failed(req.id, e.message, req.startTime)
        }
        finally {
            // use a short time-to-live for failed build
            // this is needed to allow re-try builds failed for
            // temporary error conditions e.g. expired credentials
            final ttl = resp.failed()
                    ? statusDelay.multipliedBy(10)
                    : statusDuration
            // update build status store
            buildStore.storeBuild(req.targetImage, resp, ttl)
            // cleanup build context
            if( shouldCleanup(resp) )
                buildStrategy.cleanup(req)
        }
    }

    protected boolean shouldCleanup(BuildResult result) {
        if( cleanup==null )
            return !debugMode
        if( cleanup == 'true' )
            return true
        if( cleanup == 'false' )
            return false
        if( cleanup.toLowerCase() == 'onsuccess' ) {
            return result?.exitStatus==0
        }
        log.debug "Invalid cleanup value: '$cleanup'"
        return true
    }

    protected CompletableFuture<BuildResult> launchAsync(BuildRequest request) {
        // check the build rate limit
        try {
            if( rateLimiterService )
                rateLimiterService.acquireBuild(new AcquireRequest(request.user?.id?.toString(), request.ip))
        }
        catch (Exception e) {
            buildStore.removeBuild(request.targetImage)
            throw e
        }
        // launch the build async
        CompletableFuture
                .<BuildResult>supplyAsync(() -> launch(request), executor)
                .thenApply((result) -> { notifyCompletion(request,result); return result })
    }

    protected notifyCompletion(BuildRequest request, BuildResult result) {
        eventPublisher.publishEvent(new BuildEvent(request, result))
    }

    protected void checkOrSubmit(BuildRequest request) {
        // try to store a new build status for the given target image
        // this returns true if and only if such container image was not set yet
        final ret1 = BuildResult.create(request)
        if( buildStore.storeIfAbsent(request.targetImage, ret1) ) {
            // go ahead
            log.info "== Submit build request request: $request"
            launchAsync(request)
            return
        }
        // since it was unable to initialise the build result status
        // this means the build status already exists, retrieve it
        final ret2 = buildStore.getBuild(request.targetImage)
        if( ret2 ) {
            log.info "== Hit build cache for request: $request"
            return
        }
        // invalid state
        throw new IllegalStateException("Unable to determine build status for '$request.targetImage'")
    }

}
