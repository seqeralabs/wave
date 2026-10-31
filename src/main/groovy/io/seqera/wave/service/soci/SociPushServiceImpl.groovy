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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.json.JsonOutput
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
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V2_TYPE
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1
import static io.seqera.wave.service.job.JobHelper.saveDockerAuth
/**
 * Publish the SOCI-enabled tag of durable containers: once the container exists, its layers are
 * indexed (see {@link SociIndexService}) and a {@code soci-indexer push} job uploads the SOCI
 * documents to the container repository, under the tag {@code <tag>-soci}. The original tag is
 * never changed.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Named('SociPush')
@Requires(bean = SociEnabled)
@CompileStatic
class SociPushServiceImpl implements SociPushService, JobHandler<SociPushEntry> {

    static final String SOCI_TAG_SUFFIX = '-soci'

    @Inject
    private SociConfig config

    @Inject
    private SociIndexService indexService

    @Inject
    private SociPushStateStore store

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
     * @return The SOCI-enabled image of a container e.g. {@code quay.io/user/app:1.0-soci}
     */
    static String sociImage(String targetImage) {
        final coords = ContainerCoordinates.parse(targetImage)
        return "${coords.registry}/${coords.image}:${coords.reference}${SOCI_TAG_SUFFIX}".toString()
    }

    @Override
    String sociTarget(String targetImage, ContainerPlatform platform, PlatformId identity) {
        final soci = sociImage(targetImage)
        if( exists(soci, identity) )
            return soci
        if( store.putIfAbsent(soci, SociPushEntry.of(new SociPushRequest(targetImage: targetImage, sociImage: soci))) ) {
            CompletableFuture.runAsync(() -> publish(targetImage, soci, platform, identity), ioExecutor)
        }
        return targetImage
    }

    protected boolean exists(String image, PlatformId identity) {
        try {
            return proxyService.getManifestContent(image, identity) != null
        }
        catch (Exception e) {
            log.warn "Unable to check image '${image}' - cause: ${e.message}"
            return false
        }
    }

    protected void publish(String targetImage, String sociImage, ContainerPlatform platform, PlatformId identity) {
        try {
            final docs = prepare(targetImage, platform, identity)
            if( docs==null ) {
                store.putEntry(SociPushEntry.of(new SociPushRequest(targetImage: targetImage, sociImage: sociImage)).complete(null, 'Nothing to index'))
                return
            }
            final authJson = inspectService.credentialsConfigJson("FROM ${config.repo}", targetImage, null, identity)
            final request = SociPushRequest.create(targetImage, sociImage, workspace(), authJson)
            saveDockerAuth(request.workDir, authJson)
            Files.write(request.workDir.resolve('plan.json'), pushPlan(docs, sociImage).bytes)
            store.put(sociImage, SociPushEntry.of(request))
            log.info "== SOCI push submitted - request=${request}"
            jobService.launchSociPush(request)
        }
        catch (Throwable t) {
            log.warn "== SOCI push failed for image '${targetImage}' - cause: ${t.message}", t
            store.putEntry(SociPushEntry.of(new SociPushRequest(targetImage: targetImage, sociImage: sociImage)).complete(null, t.message ?: t.toString()))
        }
    }

    /**
     * The SOCI documents of a container, available once its layers are indexed
     *
     * @param index The image index to push, with the SOCI index descriptors
     * @param docs The SOCI documents of each indexed platform manifest
     */
    static class Prepared {
        String index
        Collection<SociDocs> docs
    }

    /**
     * Wait for the container and the indexing of its layers, then create its SOCI documents
     *
     * @return The SOCI documents or {@code null} when the container has no layer to index
     */
    protected Prepared prepare(String targetImage, ContainerPlatform platform, PlatformId identity) {
        final deadline = Instant.now().plus(config.publishTimeout)
        final content = awaitManifest(targetImage, identity, deadline)
        final coords = ContainerCoordinates.parse(targetImage)
        final repository = "${coords.registry}/${coords.image}".toString()
        final json = new JsonSlurper().parseText(content) as Map
        final isIndex = json.mediaType==OCI_IMAGE_INDEX_V1 || json.mediaType==DOCKER_IMAGE_INDEX_V2
        // the platform manifests to index, by digest
        final manifests = isIndex
                ? platformManifests(repository, json, platform, identity)
                : [(io.seqera.wave.util.RegHelper.digest(content)): content]
        final docs = new LinkedHashMap<String,SociDocs>()
        for( Map.Entry<String,String> it : manifests ) {
            final layers = SociManifestAssembler.eligibleLayers(it.value, config.minLayerSize)
            if( !layers )
                continue
            final ztocs = awaitZtocs(repository, layers, identity, deadline)
            docs.put(it.key, SociManifestAssembler.assemble(it.value, ztocs, config.spanSize))
        }
        if( !docs )
            return null
        final index = isIndex
                ? SociManifestAssembler.updateIndex(content, docs)
                : SociManifestAssembler.wrapInIndex(docs.values().first(), configPlatform(repository, json, platform, identity))
        return new Prepared(index: index, docs: docs.values())
    }

    protected String awaitManifest(String image, PlatformId identity, Instant deadline) {
        while( true ) {
            final content = proxyService.getManifestContent(image, identity)
            if( content )
                return content
            if( Instant.now().isAfter(deadline) )
                throw new IllegalStateException("Timeout waiting for image '${image}'")
            sleep(config.statusDelay.toMillis())
        }
    }

    protected Map<String,ZtocEntry> awaitZtocs(String repository, List<Map> layers, PlatformId identity, Instant deadline) {
        final digests = layers.collect((Map it) -> it.digest as String)
        indexService.indexLayers(repository, layers, identity)
        while( true ) {
            final ztocs = indexService.findZtocs(digests)
            if( ztocs.keySet().containsAll(digests) )
                return ztocs
            if( Instant.now().isAfter(deadline) )
                throw new IllegalStateException("Timeout waiting for the SOCI index of '${repository}' - indexed ${ztocs.size()} of ${digests.size()} layers")
            sleep(config.statusDelay.toMillis())
            // re-submit the layers whose indexing failed
            indexService.indexLayers(repository, layers, identity)
        }
    }

    protected Map<String,String> platformManifests(String repository, Map index, ContainerPlatform platform, PlatformId identity) {
        final result = new LinkedHashMap<String,String>()
        for( Map entry : (index.manifests as List<Map>) ) {
            final media = entry.mediaType as String
            if( media!=OCI_IMAGE_MANIFEST_V1 && media!=DOCKER_MANIFEST_V2_TYPE )
                continue
            if( entry.artifactType || !entry.platform )
                continue
            if( !platform.platforms.any((ContainerPlatform.Platform it) -> it.matches(entry.platform as Map)) )
                continue
            final manifest = proxyService.getManifestContent("${repository}@${entry.digest}", identity)
            if( manifest )
                result.put(entry.digest as String, manifest)
        }
        return result
    }

    /**
     * The platform of a single image manifest, read from its config blob, used to wrap it in an image index
     */
    protected Map configPlatform(String repository, Map manifest, ContainerPlatform platform, PlatformId identity) {
        final result = new LinkedHashMap<String,Object>()
        result.architecture = platform.arch
        result.os = platform.os ?: 'linux'
        try {
            final config = proxyService.getBlobContent("\${repository}@\${(manifest.config as Map).digest}", identity)
            final json = new JsonSlurper().parseText(config) as Map
            if( json.architecture )
                result.architecture = json.architecture
            if( json.os )
                result.os = json.os
            if( json.variant )
                result.variant = json.variant
        }
        catch (Exception e) {
            log.warn "Unable to read the config of '\${repository}' - cause: \${e.message}"
        }
        return result
    }

    /**
     * Create the plan of the {@code soci-indexer push} command: the SOCI index config and the zTOC blobs
     * (copied from the zTOC repository), then the SOCI index manifests, the annotated image manifests,
     * and finally the image index tagged {@code <tag>-soci}
     */
    protected String pushPlan(Prepared prepared, String sociImage) {
        final blobs = new ArrayList<Map>()
        final manifests = new ArrayList<Map>()
        blobs.add([digest: SociManifestAssembler.EMPTY_JSON_DIGEST, size: 2, source: 'base64:' + SociManifestAssembler.EMPTY_JSON.bytes.encodeBase64().toString()])
        for( SociDocs docs : prepared.docs ) {
            for( ZtocEntry it : docs.ztocs )
                blobs.add([digest: it.ztocDigest, size: it.ztocSize, source: "docker://${config.repo}@${it.ztocDigest}".toString()])
            manifests.add(manifest(docs.sociIndexDigest, OCI_IMAGE_MANIFEST_V1, docs.sociIndexJson))
            manifests.add(manifest(docs.manifestDigest, OCI_IMAGE_MANIFEST_V1, docs.manifestJson))
        }
        manifests.add(manifest(ContainerCoordinates.parse(sociImage).reference, OCI_IMAGE_INDEX_V1, prepared.index))
        return JsonOutput.toJson([blobs: blobs.unique((Map it) -> it.digest), manifests: manifests])
    }

    static protected Map manifest(String reference, String mediaType, String body) {
        return [reference: reference, mediaType: mediaType, body: body.bytes.encodeBase64().toString()]
    }

    protected Path workspace() {
        final dir = buildConfig?.buildWorkspace ?: System.getProperty('java.io.tmpdir')
        return Path.of(dir).toAbsolutePath()
    }

    @Override
    SociPushEntry getJobEntry(JobSpec job) {
        return store.get(job.entryKey)
    }

    @Override
    JobSpec launchJob(JobSpec job, SociPushEntry entry) {
        final request = entry.request
        final coords = ContainerCoordinates.parse(request.sociImage)
        final command = List.of(
                'soci-indexer', 'push',
                '-target', "${coords.registry}/${coords.image}".toString(),
                '-plan', request.workDir.resolve('plan.json').toString() )
        strategy.launchCommand(job.operationName, request.workDir, command)
        return job.withLaunchTime(Instant.now())
    }

    @Override
    void onJobCompletion(JobSpec job, SociPushEntry entry, JobState state) {
        meterRegistry?.counter('wave.soci.push.jobs', 'result', state.succeeded() ? 'succeeded' : 'failed')?.increment()
        if( state.succeeded() ) {
            log.info "== SOCI push completed - job=${job.operationName}; image=${entry.request.sociImage}"
            store.putEntry(entry.complete(state.exitCode, null))
        }
        else {
            log.warn "== SOCI push failed - job=${job.operationName}; image=${entry.request.sociImage}; state=${state}"
            store.putEntry(entry.complete(state.exitCode, state.stdout ?: 'Unknown error'))
        }
    }

    @Override
    void onJobException(JobSpec job, SociPushEntry entry, Throwable error) {
        log.error("== SOCI push errored - job=${job.operationName}; image=${entry.request.sociImage}", error)
        store.putEntry(entry.complete(null, error.message ?: error.toString()))
    }

    @Override
    void onJobTimeout(JobSpec job, SociPushEntry entry) {
        log.warn "== SOCI push timed out - job=${job.operationName}; image=${entry.request.sociImage}"
        store.putEntry(entry.complete(null, 'SOCI push job timed out'))
    }
}
