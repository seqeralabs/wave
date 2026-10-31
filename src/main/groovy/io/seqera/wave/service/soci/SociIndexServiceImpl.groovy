/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.soci

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.configuration.SociEnabled
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.soci.strategy.SociIndexStrategy
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.model.ContentType.DOCKER_IMAGE_INDEX_V2
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.service.job.JobHelper.saveDockerAuth
/**
 * Build the zTOC of image layers with a {@code soci-indexer} job and look them up.
 *
 * Each zTOC is pushed to the repository {@code wave.soci.repo} as an OCI artifact tagged
 * {@code ztoc-<span-size>-<layer-digest-hex>}, whose annotations describe the zTOC. The repository is
 * the source of truth, the {@link SociZtocStore} caches the lookups.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Named('SociIndex')
@Requires(bean = SociEnabled)
@CompileStatic
class SociIndexServiceImpl implements SociIndexService, JobHandler<SociIndexEntry> {

    static final String ANNOTATION_LAYER_DIGEST = 'io.seqera.wave.ztoc.layer-digest'
    static final String ANNOTATION_LAYER_MEDIA_TYPE = 'io.seqera.wave.ztoc.layer-mediaType'
    static final String ANNOTATION_ZTOC_DIGEST = 'io.seqera.wave.ztoc.digest'
    static final String ANNOTATION_ZTOC_SIZE = 'io.seqera.wave.ztoc.size'
    static final String ANNOTATION_SPAN_SIZE = 'io.seqera.wave.ztoc.span-size'

    @Inject
    private SociConfig config

    @Inject
    private SociZtocStore ztocStore

    @Inject
    private SociIndexStateStore jobStore

    @Inject
    private JobService jobService

    @Inject
    private SociIndexStrategy strategy

    @Inject
    private RegistryProxyService proxyService

    @Inject
    private ContainerInspectService inspectService

    @Inject
    @Nullable
    private BuildConfig buildConfig

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    @Inject
    @Nullable
    private MeterRegistry meterRegistry

    /**
     * @return The tag of the zTOC artifact of a layer
     */
    static String ztocTag(String layerDigest, long spanSize) {
        return "ztoc-${spanSize}-${layerDigest.replace('sha256:','')}"
    }

    @Override
    Map<String,ZtocEntry> findZtocs(Collection<String> layerDigests) {
        final result = new LinkedHashMap<String,ZtocEntry>()
        for( String digest : layerDigests ) {
            final entry = findZtoc(digest)
            if( entry )
                result.put(digest, entry)
        }
        return result
    }

    protected ZtocEntry findZtoc(String layerDigest) {
        final key = SociIndexRequest.key(layerDigest, config.spanSize)
        final cached = ztocStore.get(key)
        if( cached )
            return cached
        // a job is running (or failed recently) for this layer, no need to look up the repository
        if( jobStore.get(key) )
            return null
        final found = lookupZtoc(layerDigest)
        if( found )
            ztocStore.put(key, found)
        return found
    }

    /**
     * Look up the zTOC artifact of a layer in the zTOC repository
     */
    protected ZtocEntry lookupZtoc(String layerDigest) {
        final image = "${config.repo}:${ztocTag(layerDigest, config.spanSize)}"
        final manifest = proxyService.getManifestContent(image, null)
        return manifest ? parseZtocArtifact(manifest) : null
    }

    static protected ZtocEntry parseZtocArtifact(String manifest) {
        final json = new JsonSlurper().parseText(manifest) as Map
        final annotations = (json.annotations ?: [:]) as Map<String,String>
        if( !annotations.get(ANNOTATION_ZTOC_DIGEST) )
            return null
        return new ZtocEntry(
                annotations.get(ANNOTATION_LAYER_DIGEST),
                annotations.get(ANNOTATION_LAYER_MEDIA_TYPE),
                annotations.get(ANNOTATION_ZTOC_DIGEST),
                annotations.get(ANNOTATION_ZTOC_SIZE) as long,
                annotations.get(ANNOTATION_SPAN_SIZE) as long )
    }

    @Override
    void indexLayers(String repository, List<Map> layers, PlatformId identity) {
        final missing = layers.findAll((Map it) -> !findZtoc(it.digest as String))
        if( !missing )
            return
        // credentials to pull the layers and to push the zTOC artifacts
        final authJson = inspectService.credentialsConfigJson("FROM ${repository}", config.repo, null, identity)
        for( Map layer : missing ) {
            launchIndex(repository, layer, authJson)
        }
    }

    protected void launchIndex(String repository, Map layer, String authJson) {
        final digest = layer.digest as String
        final request = SociIndexRequest.create(
                "${repository}@${digest}",
                digest,
                layer.mediaType as String,
                layer.size as long,
                config.spanSize,
                config.minLayerSize,
                config.repo,
                workspace(),
                authJson )
        if( jobStore.putIfAbsent(request.key, SociIndexEntry.of(request)) ) {
            log.info "== SOCI index submitted - request=${request}"
            jobService.launchSociIndex(request)
        }
    }

    protected Path workspace() {
        final dir = buildConfig?.buildWorkspace ?: System.getProperty('java.io.tmpdir')
        return Path.of(dir).toAbsolutePath()
    }

    @Override
    CompletableFuture<Void> requestIndex(String containerImage, ContainerPlatform platform, PlatformId identity) {
        return CompletableFuture.runAsync(() -> requestIndex0(containerImage, platform, identity), ioExecutor)
    }

    protected void requestIndex0(String containerImage, ContainerPlatform platform, PlatformId identity) {
        try {
            final coords = ContainerCoordinates.parse(containerImage)
            final repository = "${coords.registry}/${coords.image}".toString()
            for( String manifest : platformManifests(containerImage, repository, platform, identity) ) {
                indexLayers(repository, SociManifestAssembler.eligibleLayers(manifest, config.minLayerSize), identity)
            }
        }
        catch (Throwable t) {
            log.warn "Unable to request SOCI index for image '${containerImage}' - cause: ${t.message}", t
        }
    }

    /**
     * @return The image manifests of a container matching the given platform
     */
    protected List<String> platformManifests(String containerImage, String repository, ContainerPlatform platform, PlatformId identity) {
        final content = proxyService.getManifestContent(containerImage, identity)
        if( !content )
            return List.<String>of()
        final json = new JsonSlurper().parseText(content) as Map
        if( json.mediaType != OCI_IMAGE_INDEX_V1 && json.mediaType != DOCKER_IMAGE_INDEX_V2 )
            return List.of(content)
        final result = new ArrayList<String>()
        for( Map entry : (json.manifests as List<Map>) ) {
            if( !entry.platform || !platform.platforms.any((ContainerPlatform.Platform it) -> it.matches(entry.platform as Map)) )
                continue
            final manifest = proxyService.getManifestContent("${repository}@${entry.digest}", identity)
            if( manifest )
                result.add(manifest)
        }
        return result
    }

    @Override
    SociIndexEntry getJobEntry(JobSpec job) {
        return jobStore.get(job.entryKey)
    }

    @Override
    JobSpec launchJob(JobSpec job, SociIndexEntry entry) {
        final request = entry.request
        saveDockerAuth(request.workDir, request.authJson)
        strategy.launchJob(job.operationName, request)
        return job.withLaunchTime(Instant.now())
    }

    @Override
    void onJobCompletion(JobSpec job, SociIndexEntry entry, JobState state) {
        final result = state.succeeded() ? parseResult(state.stdout) : null
        recordJob(entry, result!=null ? 'succeeded' : 'failed')
        if( result==null ) {
            log.warn "== SOCI index failed - job=${job.operationName}; layer=${entry.request.layerDigest}; state=${state}"
            jobStore.putEntry(entry.complete(state.exitCode, null, state.stdout ?: 'Unknown error'))
            return
        }
        final ztoc = result.skipped ? null : toZtoc(result)
        if( ztoc )
            ztocStore.put(entry.key, ztoc)
        jobStore.putEntry(entry.complete(state.exitCode, ztoc, null))
        log.info "== SOCI index completed - job=${job.operationName}; layer=${entry.request.layerDigest}; ztoc=${ztoc?.ztocDigest}; skipped=${result.skipped}; reason=${result.reason}"
    }

    @Override
    void onJobException(JobSpec job, SociIndexEntry entry, Throwable error) {
        recordJob(entry, 'failed')
        log.error("== SOCI index errored - job=${job.operationName}; layer=${entry.request.layerDigest}", error)
        jobStore.putEntry(entry.complete(null, null, error.message ?: error.toString()))
    }

    @Override
    void onJobTimeout(JobSpec job, SociIndexEntry entry) {
        recordJob(entry, 'timeout')
        log.warn "== SOCI index timed out - job=${job.operationName}; layer=${entry.request.layerDigest}"
        jobStore.putEntry(entry.complete(null, null, 'SOCI index job timed out'))
    }

    protected void recordJob(SociIndexEntry entry, String result) {
        meterRegistry?.counter('wave.soci.index.jobs', 'result', result)?.increment()
        if( entry.request.creationTime )
            meterRegistry?.timer('wave.soci.index.duration', 'result', result)?.record(java.time.Duration.between(entry.request.creationTime, Instant.now()))
    }

    /**
     * Parse the result line printed by {@code soci-indexer index}, the last JSON object of the job output
     */
    static protected Map parseResult(String stdout) {
        if( !stdout )
            return null
        final lines = stdout.readLines().reverse()
        for( String line : lines ) {
            final it = line.trim()
            if( !it.startsWith('{') || !it.contains('"layerDigest"') )
                continue
            try {
                return new JsonSlurper().parseText(it) as Map
            }
            catch (Exception e) {
                log.debug "Invalid SOCI index result line: ${it} - cause: ${e.message}"
            }
        }
        return null
    }

    static protected ZtocEntry toZtoc(Map result) {
        if( !result.ztocDigest )
            return null
        return new ZtocEntry(
                result.layerDigest as String,
                result.layerMediaType as String,
                result.ztocDigest as String,
                result.ztocSize as long,
                result.spanSize as long )
    }
}
