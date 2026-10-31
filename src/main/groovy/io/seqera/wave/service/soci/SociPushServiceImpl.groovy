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
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.configuration.SociEnabled
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.soci.strategy.SociIndexStrategy
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.model.ContentType.DOCKER_IMAGE_INDEX_V2
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1
import static io.seqera.wave.service.job.JobHelper.saveDockerAuth
import static io.seqera.wave.service.soci.SociPushEntry.Status.FAILED
import static io.seqera.wave.service.soci.SociPushEntry.Status.PUSHING
import static io.seqera.wave.service.soci.SociPushEntry.Status.SUCCEEDED
import static io.seqera.wave.service.soci.SociPushEntry.Status.WAITING_CONTAINER
import static io.seqera.wave.service.soci.SociPushEntry.Status.WAITING_INDEX
/**
 * Publish the SOCI-enabled tag of durable containers, driven by events:
 * <ol>
 *     <li>the container request registers the publication, waiting for the container;</li>
 *     <li>the build event or the mirror completion starts it: the missing layers are indexed;</li>
 *     <li>each layer indexing completion re-checks the publications waiting for it;</li>
 *     <li>once all the layers are indexed, a {@code soci-indexer push} job uploads the SOCI documents
 *     under the tag {@code <tag>-soci}. The original tag is never changed.</li>
 * </ol>
 * Pending publications expire after {@code wave.soci.publish-timeout}.
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

    /** annotation of the SOCI image index holding the digest of the target image it was created from */
    static final String ANNOTATION_SOURCE_DIGEST = 'io.seqera.wave.soci.source-digest'

    @Inject
    private SociConfig config

    @Inject
    private SociIndexService indexService

    @Inject
    private SociPushStateStore store

    @Inject
    private SociWaitersStore waiters

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
        // a container referenced by digest cannot have a sibling tag
        if( ContainerCoordinates.parse(targetImage).reference?.startsWith('sha256:') )
            return targetImage
        final soci = sociImage(targetImage)
        final entry = store.get(soci)
        // a recently published tag is assumed current, no registry request
        if( entry?.succeeded() )
            return soci
        // a publication is pending or failed recently
        if( entry )
            return targetImage
        final target = getManifest(targetImage, identity)
        if( target && isCurrent(soci, target, identity) ) {
            store.putEntry(SociPushEntry.of(SociPushRequest.create(targetImage, soci, platform, identity, workspace()), SUCCEEDED))
            return soci
        }
        final pending = SociPushEntry.of(SociPushRequest.create(targetImage, soci, platform, identity, workspace()), WAITING_CONTAINER)
        if( store.putIfAbsent(soci, pending) && target ) {
            // the container exists already e.g. a cached build, there is no event to wait for
            CompletableFuture.runAsync(() -> start(pending), ioExecutor)
        }
        return targetImage
    }

    protected String getManifest(String image, PlatformId identity) {
        try {
            return proxyService.getManifestContent(image, identity)
        }
        catch (Exception e) {
            log.warn "Unable to fetch manifest of '${image}' - cause: ${e.message}"
            return null
        }
    }

    /**
     * @return {@code true} when the SOCI image exists and was published from the given content of the target image
     */
    protected boolean isCurrent(String sociImage, String target, PlatformId identity) {
        final soci = getManifest(sociImage, identity)
        if( !soci )
            return false
        final annotations = ((new JsonSlurper().parseText(soci) as Map).annotations ?: [:]) as Map
        return annotations.get(ANNOTATION_SOURCE_DIGEST) == RegHelper.digest(target)
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        onContainerCompletion(event.request.targetImage, event.result.succeeded())
    }

    @Override
    void onContainerCompletion(String targetImage, boolean succeeded) {
        final entry = store.get(sociImage(targetImage))
        if( entry?.status!=WAITING_CONTAINER )
            return
        if( !succeeded ) {
            fail(entry, "Container ${targetImage} failed")
            return
        }
        CompletableFuture.runAsync(() -> start(entry), ioExecutor)
    }

    /**
     * Start a publication: request the indexing of the missing layers, or push when all are indexed
     */
    protected void start(SociPushEntry entry) {
        try {
            final waiting = entry.withStatus(WAITING_INDEX)
            store.putEntry(waiting)
            final layers = layersToIndex(entry.request)
            final missing = missingLayers(layers)
            if( !missing ) {
                push(waiting)
                return
            }
            for( Map it : missing )
                waiters.add(it.digest as String, entry.key)
            indexService.indexLayers(repository(entry.request.targetImage), missing, entry.request.identity)
            // the layers may have been indexed meanwhile
            if( !missingLayers(layers) )
                push(waiting)
        }
        catch (Throwable t) {
            log.warn "== SOCI push failed for image '${entry.request.targetImage}' - cause: ${t.message}", t
            fail(entry, t.message ?: t.toString())
        }
    }

    @EventListener
    void onLayerEvent(SociLayerEvent event) {
        final images = waiters.get(event.layerDigest)?.sociImages ?: List.<String>of()
        for( String soci : images ) {
            final entry = store.get(soci)
            if( entry?.status!=WAITING_INDEX )
                continue
            if( !event.succeeded ) {
                fail(entry, "Unable to index layer ${event.layerDigest}")
                continue
            }
            CompletableFuture.runAsync(() -> pushWhenIndexed(entry), ioExecutor)
        }
    }

    protected void pushWhenIndexed(SociPushEntry entry) {
        try {
            if( !missingLayers(layersToIndex(entry.request)) )
                push(entry)
        }
        catch (Throwable t) {
            log.warn "== SOCI push failed for image '${entry.request.targetImage}' - cause: ${t.message}", t
            fail(entry, t.message ?: t.toString())
        }
    }

    /**
     * @return The eligible layers of the target image platform manifests
     */
    protected List<Map> layersToIndex(SociPushRequest request) {
        final result = new ArrayList<Map>()
        for( String manifest : platformManifests(request).values() )
            result.addAll(SociManifestAssembler.eligibleLayers(manifest, config.minLayerSize))
        return result.unique((Map it) -> it.digest)
    }

    protected List<Map> missingLayers(List<Map> layers) {
        final ztocs = indexService.findZtocs(layers.collect((Map it) -> it.digest as String))
        return layers.findAll((Map it) -> !ztocs.containsKey(it.digest as String))
    }

    /**
     * Prepare the SOCI documents and launch the push job; the publication is pushed at most once
     */
    protected void push(SociPushEntry entry) {
        final current = store.get(entry.key)
        if( current?.status!=WAITING_INDEX )
            return
        // claim the push, a concurrent layer event finds it in PUSHING status
        if( !store.putIfAbsent("push/${entry.key}".toString(), entry.withStatus(PUSHING), config.jobTimeout) )
            return
        final prepared = prepare(entry.request)
        if( prepared==null ) {
            fail(entry, 'Nothing to index')
            return
        }
        final request = entry.request
        final authJson = inspectService.credentialsConfigJson("FROM ${config.repo}", request.targetImage, null, request.identity)
        saveDockerAuth(request.workDir, authJson)
        Files.write(request.workDir.resolve('plan.json'), pushPlan(prepared, request.sociImage).bytes)
        store.putEntry(entry.withStatus(PUSHING))
        log.info "== SOCI push submitted - request=${request}"
        jobService.launchSociPush(request)
    }

    /**
     * The SOCI documents of a container
     *
     * @param index The image index to push, with the SOCI index descriptors
     * @param docs The SOCI documents of each indexed platform manifest
     */
    static class Prepared {
        String index
        Collection<SociDocs> docs
    }

    /**
     * Create the SOCI documents of the container, whose layers are all indexed
     *
     * @return The SOCI documents or {@code null} when the container has no layer to index
     */
    protected Prepared prepare(SociPushRequest request) {
        final content = proxyService.getManifestContent(request.targetImage, request.identity)
        if( !content )
            throw new IllegalStateException("Missing image '${request.targetImage}'")
        final json = new JsonSlurper().parseText(content) as Map
        final docs = new LinkedHashMap<String,SociDocs>()
        for( Map.Entry<String,String> it : platformManifests(request, content).entrySet() ) {
            final layers = SociManifestAssembler.eligibleLayers(it.value, config.minLayerSize)
            if( !layers )
                continue
            final ztocs = indexService.findZtocs(layers.collect((Map l) -> l.digest as String))
            docs.put(it.key, SociManifestAssembler.assemble(it.value, ztocs, config.spanSize))
        }
        if( !docs )
            return null
        final index = isIndex(json)
                ? SociManifestAssembler.updateIndex(content, docs)
                : SociManifestAssembler.wrapInIndex(docs.values().first(), imagePlatform(request, json))
        return new Prepared(index: withSourceDigest(index, RegHelper.digest(content)), docs: docs.values())
    }

    static protected boolean isIndex(Map json) {
        return json.mediaType==OCI_IMAGE_INDEX_V1 || json.mediaType==DOCKER_IMAGE_INDEX_V2
    }

    static protected String withSourceDigest(String index, String digest) {
        final json = new JsonSlurper().parseText(index) as Map
        final annotations = new TreeMap<String,Object>((json.annotations ?: [:]) as Map)
        annotations.put(ANNOTATION_SOURCE_DIGEST, digest)
        json.annotations = annotations
        return JsonOutput.toJson(json)
    }

    protected Map<String,String> platformManifests(SociPushRequest request) {
        final content = proxyService.getManifestContent(request.targetImage, request.identity)
        if( !content )
            throw new IllegalStateException("Missing image '${request.targetImage}'")
        return platformManifests(request, content)
    }

    /**
     * @return The platform manifests of the target image, by digest
     */
    protected Map<String,String> platformManifests(SociPushRequest request, String content) {
        final json = new JsonSlurper().parseText(content) as Map
        if( !isIndex(json) )
            return Map.of(RegHelper.digest(content), content)
        final result = new LinkedHashMap<String,String>()
        final repository = repository(request.targetImage)
        for( Map entry : SociPlatforms.targetEntries(json, request.platform) ) {
            final manifest = proxyService.getManifestContent("${repository}@${entry.digest}", request.identity)
            if( manifest )
                result.put(entry.digest as String, manifest)
        }
        return result
    }

    protected Map imagePlatform(SociPushRequest request, Map manifest) {
        String config = null
        try {
            config = proxyService.getBlobContent("${repository(request.targetImage)}@${(manifest.config as Map).digest}", request.identity)
        }
        catch (Exception e) {
            log.warn "Unable to read the config of '${request.targetImage}' - cause: ${e.message}"
        }
        return SociPlatforms.platformOf(config, request.platform)
    }

    static protected String repository(String image) {
        final coords = ContainerCoordinates.parse(image)
        return "${coords.registry}/${coords.image}".toString()
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

    protected java.nio.file.Path workspace() {
        return SociPlatforms.workspace(buildConfig)
    }

    protected void fail(SociPushEntry entry, String error) {
        log.warn "== SOCI push failed - image=${entry.request.sociImage}; cause=${error}"
        store.putEntry(entry.failed(error))
    }

    @Override
    SociPushEntry getJobEntry(JobSpec job) {
        return store.get(job.entryKey)
    }

    @Override
    JobSpec launchJob(JobSpec job, SociPushEntry entry) {
        final request = entry.request
        final command = List.of(
                'soci-indexer', 'push',
                '-target', repository(request.sociImage),
                '-plan', request.workDir.resolve('plan.json').toString() )
        strategy.launchCommand(job.operationName, request.workDir, command)
        return job.withLaunchTime(Instant.now())
    }

    @Override
    void onJobCompletion(JobSpec job, SociPushEntry entry, JobState state) {
        meterRegistry?.counter('wave.soci.push.jobs', 'result', state.succeeded() ? 'succeeded' : 'failed')?.increment()
        store.remove("push/${entry.key}".toString())
        if( state.succeeded() ) {
            log.info "== SOCI push completed - job=${job.operationName}; image=${entry.request.sociImage}"
            store.putEntry(entry.withStatus(SUCCEEDED))
        }
        else
            fail(entry, state.stdout ?: 'Unknown error')
    }

    @Override
    void onJobException(JobSpec job, SociPushEntry entry, Throwable error) {
        log.error("== SOCI push errored - job=${job.operationName}; image=${entry.request.sociImage}", error)
        store.remove("push/${entry.key}".toString())
        fail(entry, error.message ?: error.toString())
    }

    @Override
    void onJobTimeout(JobSpec job, SociPushEntry entry) {
        store.remove("push/${entry.key}".toString())
        fail(entry, 'SOCI push job timed out')
    }
}
