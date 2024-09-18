/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.builder

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.core.annotation.Nullable
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.api.BuildContext
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.HttpServerRetryableErrorException
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.RateLimiterService
import io.seqera.wave.service.builder.store.BuildRecordStore
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.stream.StreamService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.Retryable
import io.seqera.wave.util.TarUtils
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.util.RegHelper.layerDir
import static io.seqera.wave.util.RegHelper.layerName
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
@Named('Build')
@CompileStatic
class ContainerBuildServiceImpl implements ContainerBuildService, JobHandler<BuildStoreEntry> {

    @Inject
    private BuildConfig buildConfig

    @Inject
    private ApplicationEventPublisher<BuildEvent> eventPublisher

    @Inject
    private BuildStore buildStore

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Inject
    private RegistryLookupService lookupService

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    @Inject
    private JobService jobService

    @Inject
    @Nullable
    private RateLimiterService rateLimiterService

    @Inject
    private HttpClientConfig httpClientConfig

    @Inject
    private StreamService streamService

    @Inject
    private BuildCounterStore buildCounter

    @Inject
    private PersistenceService persistenceService

    @Inject
    private MetricsService metricsService

    @Inject
    private BuildRecordStore buildRecordStore

    @Inject
    private RegistryProxyService proxyService
    
    /**
     * Build a container image for the given {@link BuildRequest}
     *
     * @param request
     *      A {@link BuildRequest} modelling the build request
     * @return
     *      The container image where the resulting image is going to be hosted
     */
    @Override
    BuildTrack buildImage(BuildRequest request) {
        return checkOrSubmit(request)
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

    protected String containerFile0(BuildRequest req, Path context) {
        // add the context dir for singularity builds
        final containerFile = req.formatSingularity()
                ? req.containerFile.replace('{{wave_context_dir}}', context.toString())
                : req.containerFile

        // render the Spack template if needed
        return containerFile
    }

    protected void launch(BuildRequest req) {
        try {
            // create the workdir path
            Files.createDirectories(req.workDir)
            // create context dir
            final context = req.workDir.resolve('context')
            try { Files.createDirectory(context) }
            catch (FileAlreadyExistsException e) { /* ignore it */ }
            // save the dockerfile
            final containerFile = req.workDir.resolve('Containerfile')
            Files.write(containerFile, containerFile0(req, context).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            // save build context
            if( req.buildContext ) {
                saveBuildContext(req.buildContext, context, req.identity)
            }
            // save the conda file
            if( req.condaFile ) {
                final condaFile = context.resolve('conda.yml')
                Files.write(condaFile, req.condaFile.bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }
            // save layers provided via the container config
            if( req.containerConfig ) {
                saveLayersToContext(req, context)
            }
            // launch the container build
            jobService.launchBuild(req)
        }
        catch (Throwable e) {
            log.error "== Container build unexpected exception: ${e.message} - request=$req", e
            final result = BuildResult.failed(req.buildId, e.message, req.startTime)
            buildStore.storeBuild(req.targetImage, new BuildStoreEntry(req, result), buildConfig.failureDuration)
        }
    }

    protected void launchAsync(BuildRequest request) {
        // check the build rate limit
        try {
            if( rateLimiterService )
                rateLimiterService.acquireBuild(new AcquireRequest(request.identity.userId as String, request.ip))
        }
        catch (Exception e) {
            buildStore.removeBuild(request.targetImage)
            throw e
        }

        //increment metrics
        CompletableFuture.supplyAsync(() -> metricsService.incrementBuildsCounter(request.identity), executor)

        // save the container request in the underlying storage (redis)
        createBuildRecord(request)

        // launch the build async
        CompletableFuture
                .runAsync(() -> launch(request), executor)
    }

    protected BuildTrack checkOrSubmit(BuildRequest request) {
        // find next build number
        final num = buildCounter.inc(request.containerId)
        request.withBuildId(String.valueOf(num))
        // try to store a new build status for the given target image
        // this returns true if and only if such container image was not set yet
        final ret1 = BuildResult.create(request)
        if( buildStore.storeIfAbsent(request.targetImage, new BuildStoreEntry(request, ret1)) ) {
            // go ahead
            log.info "== Container build submitted - request=$request"
            launchAsync(request)
            return new BuildTrack(ret1.id, request.targetImage, false)
        }
        // since it was unable to initialise the build result status
        // this means the build status already exists, retrieve it
        final ret2 = buildStore.getBuildResult(request.targetImage)
        if( ret2 ) {
            log.info "== Container build hit cache - request=$request"
            // note: mark as cached only if the build result is 'done'
            // if the build is still in progress it should be marked as not cached
            // so that the client will wait for the container completion
            return new BuildTrack(ret2.id, request.targetImage, ret2.done())
        }
        // invalid state
        throw new IllegalStateException("Unable to determine build status for '$request.targetImage'")
    }

    protected void saveLayersToContext(BuildRequest req, Path contextDir) {
        if(req.formatDocker()) {
            saveLayersToDockerContext0(req, contextDir)
        }
        else if(req.formatSingularity()) {
            saveLayersToSingularityContext0(req, contextDir)
        }
        else
            throw new IllegalArgumentException("Unknown container format: $req.format")
    }

    protected void saveLayersToDockerContext0(BuildRequest request, Path contextDir) {
        final layers = request.containerConfig.layers
        for(int i=0; i<layers.size(); i++) {
            final it = layers[i]
            final target = contextDir.resolve(layerName(it))
            final retryable = retry0("Unable to copy '${it.location}' to docker context '${contextDir}'")
            // copy the layer to the build context
            retryable.apply(()-> {
                try (InputStream stream = streamService.stream(it.location, request.identity)) {
                    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
                }
                return
            })
        }
    }

    protected void saveLayersToSingularityContext0(BuildRequest request, Path contextDir) {
        final layers = request.containerConfig.layers
        for(int i=0; i<layers.size(); i++) {
            final it = layers[i]
            final target = contextDir.resolve(layerDir(it))
            try { Files.createDirectory(target) }
            catch (FileAlreadyExistsException e) { /* ignore */ }
            // retry strategy
            final retryable = retry0("Unable to copy '${it.location} to singularity context '${contextDir}'")
            // copy the layer to the build context
            retryable.apply(()-> {
                try (InputStream stream = streamService.stream(it.location, request.identity)) {
                    TarUtils.untarGzip(stream, target)
                }
                return
            })
        }
    }

    protected void saveBuildContext(BuildContext buildContext, Path contextDir, PlatformId identity) {
        // retry strategy
        final retryable = retry0("Unable to copy '${buildContext.location} to build context '${contextDir}'")
        // copy the layer to the build context
        retryable.apply(()-> {
            try (InputStream stream = streamService.stream(buildContext.location, identity)) {
                TarUtils.untarGzip(stream, contextDir)
            }
            return
        })
    }

    private Retryable<Void> retry0(String message) {
        Retryable
                .<Void>of(httpClientConfig)
                .retryCondition((Throwable t) -> t instanceof SocketException || t instanceof HttpServerRetryableErrorException)
                .onRetry((event)-> log.warn("$message - event: $event"))
    }

    // **************************************************************
    // **               build job handle implementation
    // **************************************************************

    @Override
    BuildStoreEntry getJobRecord(JobSpec job) {
        buildStore.getBuild(job.recordId)
    }

    @Override
    void onJobCompletion(JobSpec job, BuildStoreEntry build, JobState state) {
        final buildId = build.request.buildId
        final digest = state.succeeded()
                        ? proxyService.getImageDigest(build.request, true)
                        : null
        // use a short time-to-live for failed build
        // this is needed to allow re-try builds failed for
        // temporary error conditions e.g. expired credentials
        final ttl = state.succeeded()
                ? buildConfig.statusDuration
                : buildConfig.failureDuration
        // update build status store
        final exit = state.exitCode!=null ? state.exitCode : -1
        final result = state.completed()
                ? BuildResult.completed(buildId, exit, state.stdout, job.creationTime, digest)
                : BuildResult.failed(buildId, state.stdout, job.creationTime)
        buildStore.storeBuild(job.recordId, build.withResult(result), ttl)
        eventPublisher.publishEvent(new BuildEvent(build.request, result))
        log.info "== Container build completed '${build.request.targetImage}' - operation=${job.operationName}; exit=${exit}; status=${state.status}; duration=${result.duration}"
    }

    @Override
    void onJobException(JobSpec job, BuildStoreEntry build, Throwable error) {
        final result= BuildResult.failed(build.request.buildId, error.message, job.creationTime)
        buildStore.storeBuild(job.recordId, build.withResult(result), buildConfig.failureDuration)
        eventPublisher.publishEvent(new BuildEvent(build.request, result))
        log.error("== Container build exception '${build.request.targetImage}' - operation=${job.operationName}; cause=${error.message}", error)
    }

    @Override
    void onJobTimeout(JobSpec job, BuildStoreEntry build) {
        final buildId = build.request.buildId
        final result= BuildResult.failed(buildId, "Container image build timed out '${build.request.targetImage}'", job.creationTime)
        buildStore.storeBuild(job.recordId, build.withResult(result), buildConfig.failureDuration)
        eventPublisher.publishEvent(new BuildEvent(build.request, result))
        log.warn "== Container build time out '${build.request.targetImage}'; operation=${job.operationName}; duration=${result.duration}"
    }

    // **************************************************************
    // **               build record implementation
    // **************************************************************

    @EventListener
    protected void onBuildEvent(BuildEvent event) {
        saveBuildRecord(event)
    }

    /**
     * Store a build record for the given {@link BuildRequest} object.
     *
     * This method is expected to store the build record associated with the request
     * *only* in the short term store caching system, ie. without hitting the
     * long-term SurrealDB storage
     *
     * @param request The build request that needs to be storage
     */
    protected void createBuildRecord(BuildRequest request) {
        final record0 = WaveBuildRecord.fromEvent(new BuildEvent(request))
        createBuildRecord(record0.buildId, record0)
    }

    /**
     * Store the build record associated with the specified event both in the
     * short-term cache (redis) and long-term persistence layer (surrealdb)
     *
     * @param event The {@link BuildEvent} object for which the build record needs to be stored
     */
    protected void saveBuildRecord(BuildEvent event) {
        final record0 = WaveBuildRecord.fromEvent(event)
        saveBuildRecord(record0.buildId, record0)
    }

    /**
     * Store a build record object.
     *
     * This method is expected to store the build record *only* in the short term store cache (redis),
     * ie. without hitting the long-term storage (surrealdb)
     *
     * @param buildId The Id of the build record
     * @param value The {@link WaveBuildRecord} to be stored
     */
    protected void createBuildRecord(String buildId, WaveBuildRecord value) {
        buildRecordStore.putBuildRecord(buildId, value)
    }

    /**
     * Store the specified build record  both in the short-term cache (redis)
     * and long-term persistence layer (surrealdb)
     *
     * @param buildId The Id of the build record
     * @param value The {@link WaveBuildRecord} to be stored
     */
    protected void saveBuildRecord(String buildId, WaveBuildRecord value) {
        buildRecordStore.putBuildRecord(buildId, value)
        persistenceService.saveBuild(value)
    }

    /**
     * Retrieve the build record for the specified id.
     *
     * @param buildId The ID of the build record to be retrieve
     * @return The {@link WaveBuildRecord} associated with the corresponding Id, or {@code null} if it cannot be found
     */
    @Override
    WaveBuildRecord getBuildRecord(String buildId) {
        return buildRecordStore.getBuildRecord(buildId) ?: persistenceService.loadBuild(buildId)
    }

}
