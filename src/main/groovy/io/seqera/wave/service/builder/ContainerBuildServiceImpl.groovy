/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Value
import io.micronaut.context.event.ApplicationEventPublisher
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.RateLimiterService
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.storage.reader.ContentReaderFactory
import io.seqera.wave.storage.reader.HttpServerRetryableErrorException
import io.seqera.wave.util.Retryable
import io.seqera.wave.util.SpackHelper
import io.seqera.wave.util.TarUtils
import io.seqera.wave.util.TemplateRenderer
import io.seqera.wave.util.ThreadPoolBuilder
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.RegHelper.layerDir
import static io.seqera.wave.util.RegHelper.layerName
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

    @Value('${wave.build.timeout}')
    Duration buildTimeout

    @Value('${wave.build.status.duration}')
    private Duration statusDuration

    @Value('${wave.build.status.delay}')
    private Duration statusDelay

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

    @Inject
    private MeterRegistry meterRegistry

    @Inject
    private SpackConfig spackConfig

    @Inject
    private HttpClientConfig httpClientConfig

    @Inject CleanupStrategy cleanup

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

    protected String containerFile0(BuildRequest req, Path context, SpackConfig config) {
        // add the context dir for singularity builds
        final containerFile = req.formatSingularity()
                ? req.containerFile.replace('{{wave_context_dir}}', context.toString())
                : req.containerFile

        // render the Spack template if needed
        if( req.isSpackBuild ) {
            final binding = new HashMap(2)
            binding.spack_builder_image = config.builderImage
            binding.spack_runner_image = config.runnerImage
            binding.spack_arch = SpackHelper.toSpackArch(req.getPlatform())
            binding.spack_cache_dir = config.cacheMountPath
            binding.spack_key_file = config.secretMountPath
            return new TemplateRenderer().render(containerFile, binding)
        }
        else {
            return containerFile
        }
    }

    protected BuildResult launch(BuildRequest req) {
        // launch an external process to build the container
        BuildResult resp=null
        try {
            // create the workdir path
            Files.createDirectories(req.workDir)
            // create context dir
            final context = req.workDir.resolve('context')
            try { Files.createDirectory(context) }
            catch (FileAlreadyExistsException e) { /* ignore it */ }
            // save the dockerfile
            final containerFile = req.workDir.resolve('Containerfile')
            Files.write(containerFile, containerFile0(req, context, spackConfig).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            // save build context
            if( req.buildContext ) {
                saveBuildContext(req.buildContext, context)
            }
            // save the conda file
            if( req.condaFile ) {
                final condaFile = context.resolve('conda.yml')
                Files.write(condaFile, req.condaFile.bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }
            // save the spack file
            if( req.spackFile ) {
                final spackFile = context.resolve('spack.yaml')
                Files.write(spackFile, req.spackFile.bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }
            // save layers provided via the container config
            if( req.containerConfig ) {
                saveLayersToContext(req, context)
            }
            resp = buildStrategy.build(req)
            log.info "== Build request ${req.id} completed with status=$resp.exitStatus; stdout: (see below)\n${indent(resp.logs)}"
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
            if( cleanup.shouldCleanup(resp) )
                buildStrategy.cleanup(req)
        }
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
        // increment the build counter
        incrementBuildCounter(request)

        // launch the build async
        CompletableFuture
                .<BuildResult>supplyAsync(() -> launch(request), executor)
                .thenApply((result) -> { notifyCompletion(request,result); return result })
    }

    protected void incrementBuildCounter(BuildRequest request) {
        try {
            final tags = new ArrayList<String>()
            tags.add('platform'); tags.add(request.platform.toString())
            if( request.user?.id ) {
                tags.add('userId'); tags.add(request.user.id as String)
            }
            meterRegistry.counter('wave.builds', tags as String[]).increment()
        }
        catch (Throwable e) {
            log.error "Unable to increment build counter",e
        }
    }

    protected notifyCompletion(BuildRequest request, BuildResult result) {
        eventPublisher.publishEvent(new BuildEvent(request, result))
    }

    protected void checkOrSubmit(BuildRequest request) {
        // try to store a new build status for the given target image
        // this returns true if and only if such container image was not set yet
        final ret1 = BuildResult.create(request)
        if( buildStore.storeIfAbsent(request.targetImage, ret1) ) {
            // flag it as a new build
            request.uncached = true
            // go ahead
            log.info "== Submit build request: $request"
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

    protected void saveLayersToContext(BuildRequest req, Path contextDir) {
        if(req.formatDocker()) {
            saveLayersToDockerContext0(req.containerConfig, contextDir)
        }
        else if(req.formatSingularity()) {
            saveLayersToSingularityContext0(req.containerConfig, contextDir)
        }
        else
            throw new IllegalArgumentException("Unknown container format: $req.format")
    }

    protected void saveLayersToDockerContext0(ContainerConfig config, Path contextDir) {
        final layers = config.layers
        for(int i=0; i<layers.size(); i++) {
            final it = layers[i]
            final target = contextDir.resolve(layerName(it))
            final retryable = retry0("Unable to copy '${it.location}' to docker context '${contextDir}'")
            // copy the layer to the build context
            retryable.apply(()-> {
                try (InputStream stream = ContentReaderFactory.of(it.location).openStream()) {
                    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
                }
                return
            })
        }
    }

    protected void saveLayersToSingularityContext0(ContainerConfig config, Path contextDir) {
        final layers = config.layers
        for(int i=0; i<layers.size(); i++) {
            final it = layers[i]
            final target = contextDir.resolve(layerDir(it))
            try { Files.createDirectory(target) }
            catch (FileAlreadyExistsException e) { /* ignore */ }
            // retry strategy
            final retryable = retry0("Unable to copy '${it.location} to singularity context '${contextDir}'")
            // copy the layer to the build context
            retryable.apply(()-> {
                try (InputStream stream = ContentReaderFactory.of(it.location).openStream()) {
                    TarUtils.untarGzip(stream, target)
                }
                return
            })
        }
    }

    protected void saveBuildContext(BuildContext buildContext, Path contextDir) {
        // retry strategy
        final retryable = retry0("Unable to copy '${buildContext.location} to build context '${contextDir}'")
        // copy the layer to the build context
        retryable.apply(()-> {
            try (InputStream stream = ContentReaderFactory.of(buildContext.location).openStream()) {
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
}
