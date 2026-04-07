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

package io.seqera.wave.core

import java.net.http.HttpResponse
import java.time.Instant

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpStatus
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.core.spec.ConfigSpec
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.core.spec.IndexSpec
import io.seqera.wave.core.spec.ManifestSpec
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.model.ContainerOrIndexSpec
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.storage.Storage
import io.seqera.wave.util.JacksonHelper
import io.seqera.wave.service.builder.MultiPlatformBuildService
import io.seqera.wave.util.RegHelper
import static io.seqera.wave.model.ContentType.DOCKER_IMAGE_CONFIG_V1
import static io.seqera.wave.model.ContentType.DOCKER_IMAGE_INDEX_V2
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V1_JWS_TYPE
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V1_TYPE
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V2_TYPE
import static io.seqera.wave.model.ContentType.OCI_IMAGE_CONFIG_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1
/**
 * Implement the logic of container manifest manipulation and
 * layers injections
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class ContainerAugmenter {

    @Canonical
    static class ManifestInfo {
        final String imageManifest
        final String configDigest
        final String targetDigest
        final Boolean oci
        final ManifestSpec manifestSpec
    }

    /**
     * Holds the digest and size of an augmented manifest, used to update
     * entries in a manifest index after per-platform augmentation.
     */
    @Canonical
    static class AugmentedManifest {
        final String digest
        final int size
    }
    
    private ProxyClient client
    private ContainerConfig containerConfig
    private ContainerPlatform platform
    private Storage storage

    ContainerConfig getContainerConfig() {
        return containerConfig
    }

    ContainerAugmenter withStorage(Storage cache) {
        this.storage = cache
        return this
    }

    ContainerAugmenter withClient(ProxyClient client) {
        this.client = client
        return this
    }

    ContainerAugmenter withPlatform(String value) {
        this.platform = value ? ContainerPlatform.of(value) : null
        return this
    }

    ContainerAugmenter withPlatform(ContainerPlatform platform) {
        this.platform = platform
        return this
    }

    @Deprecated
    ContainerAugmenter withContainerConfig(ContainerConfig containerConfig) {
        this.containerConfig = containerConfig
        final l = containerConfig?.layers ?: Collections.<ContainerLayer>emptyList()
        for( ContainerLayer it : l )
            it.validate()
        return this
    }

    ContainerDigestPair resolve(RoutePath route, Map<String,List<String>> headers) {
        assert route, "Missing route"
        this.platform = route.request?.platform ?: ContainerPlatform.DEFAULT
        // note: do not propagate container config when "freeze" mode is enabled, because it has been already
        // applied during the container build phase, and therefore it should be ignored by the augmenter
        if( route.request?.containerConfig && !route.request.freeze ) {
            log.debug "Augmenter resolve: platform=${this.platform}; layers=${route.request.containerConfig?.layers?.collect { it.location }}"
            // for single-arch: filters fusion layers to keep only the matching arch
            // for multi-arch: this is a no-op (returns all layers); per-platform filtering
            // happens later in augmentManifest() when the actual image architecture is known
            this.containerConfig = MultiPlatformBuildService.filterLayersForPlatform(route.request.containerConfig, this.platform)
            log.debug "Augmenter resolve: filtered layers=${this.containerConfig?.layers?.collect { it.location }}"
        }
        return resolve(route.image, route.reference, headers)
    }

    protected void checkResponseCode(HttpResponse<?> response, ContainerPath route, boolean blob) {
        final code = response.statusCode()
        final repository = route.getTargetContainer()
        final String body = response.body()?.toString()
        if( code==404 ) {
            // see errors list https://docs.docker.com/registry/spec/api/#errors-2
            final error = blob ? 'MANIFEST_BLOB_UNKNOWN' : 'MANIFEST_UNKNOWN'
            final msg = "repository '$repository' not found"
            throw new DockerRegistryException(msg, code, error)
        }

        if( code>=400 ) {
            final error = code==401 || code==403 ? 'UNAUTHORIZED' : 'UNKNOWN'
            final status = HttpStatus.valueOf(code)
            final msg = "repository '$repository' ${status.reason.toLowerCase()} (${status.code})"
            throw new DockerRegistryException(msg, code, error)
        }

        if( code != 200 ) {
            log.warn("Unexpected response code ${code} on ${response.uri()}")
        }
    }

    ContainerDigestPair resolve(String imageName, String tag, Map<String,List<String>> headers) {
        assert client, "Missing client"
        assert storage, "Missing storage"
        assert platform, "Missing 'platform' parameter"

        // resolve image tag to digest
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        final digest = resp1.headers().firstValue('docker-content-digest').orElse(null)
        if( log.isTraceEnabled() )
            log.trace "Resolve (1): image $imageName:$tag => digest=$digest; response code=${resp1.statusCode()}"
        checkResponseCode(resp1, client.route, false)

        // get manifest list for digest
        final resp2 = client.getString("/v2/$imageName/manifests/$digest", headers)
        final type = resp2.headers().firstValue('content-type').orElse(null)
        checkResponseCode(resp2, client.route, false)
        final manifestsList = resp2.body()
        if( log.isTraceEnabled() )
            log.trace "Resolve (2): image $imageName:$tag => type=$type; manifests list:\n${JsonOutput.prettyPrint(manifestsList)}"

        // when there's no container config, not much to do
        // just cache the manifest content and return the digest
        if( !containerConfig ) {
            if( log.isTraceEnabled() )
                log.trace "Resolve (3): container config provided for image=$imageName:$tag"
            final target = "$client.registry.name/v2/$imageName/manifests/$digest"
            storage.saveManifest(target, manifestsList, type, digest)
            return new ContainerDigestPair(digest,digest)
        }

        if( tag.startsWith('sha256:')) {
            // container using a digest as tag cannot be augmented because it would
            // require to alter the digest itself
            final msg = "Operation not allowed for container '$imageName@$tag'"
            throw new DockerRegistryException(msg, 400, 'UNSUPPORTED')
        }

        if( type == DOCKER_MANIFEST_V1_JWS_TYPE ) {
            final v1Digest = resolveV1Manifest(manifestsList, imageName)
            if( log.isTraceEnabled() ) {
                final target = "$client.registry.name/v2/$imageName/manifests/$v1Digest"
                final v1Manifest = storage.getManifest(target).orElse(null)
                log.trace "Resolve (4) ==> new manifest v1 digest: $v1Digest\n${JsonOutput.prettyPrint(new String(v1Manifest.bytes))}"
            }
            return new ContainerDigestPair(digest, v1Digest)
        }

        // Parse the manifest body to inspect its mediaType field.
        // This determines whether we're dealing with a manifest index (multi-platform list)
        // or a single platform manifest.
        final parsedManifest = new JsonSlurper().parseText(manifestsList) as Map
        final media = parsedManifest.mediaType as String

        // Manifest index (Docker v2 or OCI):
        // Contains a list of platform-specific manifests. Delegate to resolveImageIndex
        // which iterates over matching platform entries, augments each one separately
        // with the correct arch-specific fusion layers, and updates the index.
        // This handles both single-arch (one match) and multi-arch (multiple matches).
        if( media==DOCKER_IMAGE_INDEX_V2 || media==OCI_IMAGE_INDEX_V1 ) {
            return resolveImageIndex(imageName, tag, headers, manifestsList, digest, parsedManifest, media)
        }

        // Single platform manifest (Docker v2 or OCI):
        // No index wrapping — augment the manifest directly with fusion layers.
        if( media==DOCKER_MANIFEST_V2_TYPE || media==OCI_IMAGE_MANIFEST_V1 ) {
            final ref = ManifestSpec.of(parsedManifest)
            // targetDigest is null because there's no parent index entry to update
            final manifestInfo = new ManifestInfo(manifestsList, ref.config.digest, null, media==OCI_IMAGE_MANIFEST_V1, ref)
            final result = augmentManifest(imageName, tag, headers, manifestInfo)
            return new ContainerDigestPair(digest, result.digest)
        }

        throw new IllegalArgumentException("Unexpected media type for request '$imageName:$tag' - offending value: $media")
    }

    /**
     * Resolve an image index (manifest list) by augmenting each matching platform manifest.
     *
     * An image index is a JSON document listing platform-specific manifests (e.g. linux/amd64,
     * linux/arm64). This method iterates over those entries, finds the ones matching our target
     * platform(s), and augments each one separately with the correct arch-specific fusion layers.
     *
     * For single-arch requests (e.g. platform=linux/amd64): only one entry matches, so we
     * augment one manifest and update one index entry.
     *
     * For multi-arch requests (e.g. platform=linux/amd64,linux/arm64): multiple entries match,
     * each gets augmented with its own filtered fusion layers (amd64 gets fusion-amd64.tar.gz,
     * arm64 gets fusion-arm64.tar.gz), and all index entries are updated.
     *
     * @param imageName       the repository name (e.g. "library/ubuntu")
     * @param tag             the image tag or reference
     * @param headers         HTTP headers for upstream registry requests
     * @param manifestsList   the raw JSON string of the manifest index
     * @param originalDigest  the original digest of the unmodified index
     * @param indexJson       the already-parsed index JSON (avoids double parsing)
     * @param indexMedia      the mediaType of the index (OCI or Docker)
     * @return a pair of (original digest, new augmented index digest)
     */
    protected ContainerDigestPair resolveImageIndex(String imageName, String tag, Map<String,List<String>> headers, String manifestsList, String originalDigest, Map indexJson, String indexMedia) {
        final oci = indexMedia == OCI_IMAGE_INDEX_V1
        // each entry in this list represents a platform-specific manifest (os/arch/variant)
        final manifests = indexJson.manifests as List<Map>
        // save the original container config (with ALL fusion layers for all platforms)
        // because augmentManifest mutates this.containerConfig via filterLayersForPlatform,
        // and we need to reset it before processing each platform
        final originalConfig = this.containerConfig
        // collect old digest → (new digest, new size) for each augmented platform
        final digestUpdates = new LinkedHashMap<String, AugmentedManifest>()

        try {
            for( Map indexEntry : manifests ) {
                // each index entry has a 'platform' object with os/architecture/variant fields
                final entryPlatform = indexEntry.platform as Map
                if( !entryPlatform )
                    continue

                // verify the entry's mediaType matches what we expect for this index type:
                // OCI indexes reference OCI manifests, Docker indexes reference Docker v2 manifests
                final entryMedia = indexEntry.mediaType as String
                final expectedMedia = oci ? OCI_IMAGE_MANIFEST_V1 : DOCKER_MANIFEST_V2_TYPE
                if( entryMedia != expectedMedia )
                    continue

                // check if this entry's platform (e.g. linux/amd64) matches any of our
                // target platforms — for single-arch this matches one, for multi-arch it
                // matches each platform in turn
                if( !matchesPlatformEntry(entryPlatform) )
                    continue

                final targetDigest = indexEntry.digest as String
                if( !targetDigest )
                    continue

                // fetch the actual platform-specific manifest (not the index — the real manifest
                // that contains the layer list and config reference for this architecture)
                final resp = client.getString("/v2/$imageName/manifests/$targetDigest", headers)
                checkResponseCode(resp, client.route, false)
                final platformManifest = resp.body() as String
                // parse the manifest to extract config digest, layer info, and mediaType
                final manifestInfo = parseManifest(platformManifest, targetDigest)
                if( !manifestInfo )
                    continue

                // reset to the original unfiltered container config before each platform,
                // because augmentManifest will filter it down to only the layers matching
                // this specific architecture (e.g. keep fusion-amd64, discard fusion-arm64)
                this.containerConfig = originalConfig
                // augment this platform's manifest: fetch config, filter layers, inject layers
                final result = augmentManifest(imageName, tag, headers, manifestInfo)
                // record the mapping from old digest to new digest+size for the index update
                digestUpdates.put(targetDigest, result)
            }
        }
        finally {
            // always restore the original container config, even if an exception occurs,
            // to avoid leaving the augmenter in a partially-filtered state
            this.containerConfig = originalConfig
        }

        if( digestUpdates.isEmpty() ) {
            throw new BadRequestException("Cannot find matching platform '${platform}' in the image index for '$imageName:$tag'")
        }

        // rewrite the image index: replace old digests with new augmented digests,
        // recompute the index's own digest, and store it in the cache
        final newListDigest = updateImageIndex(imageName, manifestsList, digestUpdates, oci)
        if( log.isTraceEnabled() )
            log.trace "resolveImageIndex: new index digest: $newListDigest (updated ${digestUpdates.size()} platform entries)"
        // return: original unmodified index digest + new augmented index digest
        return new ContainerDigestPair(originalDigest, newListDigest)
    }

    /**
     * Check if a manifest index entry's platform matches any of our target platforms.
     * For single-arch (e.g. linux/amd64), the platforms list has one entry.
     * For multi-arch (e.g. linux/amd64,linux/arm64), it has multiple entries.
     * Delegates to Platform.matches() which handles arch aliases (x86_64→amd64)
     * and variant normalization (arm64/v8→arm64).
     */
    protected boolean matchesPlatformEntry(Map entryPlatform) {
        return platform.platforms.any { it.matches(entryPlatform) }
    }

    /**
     * Augment a single platform manifest by injecting fusion layers and config changes.
     *
     * This is the core augmentation logic shared by both single-manifest and index-based
     * resolution. It performs three steps:
     * 1. Fetch the image config blob to determine the actual architecture
     * 2. Filter fusion layers to keep only those matching the resolved architecture
     *    (e.g. for amd64: keep fusion-amd64.tar.gz, discard fusion-arm64.tar.gz)
     * 3. Inject the filtered layers into both the image config and manifest
     *
     * NOTE: This method mutates this.containerConfig via filterLayersForPlatform.
     * When called from resolveImageIndex, the caller must reset this.containerConfig
     * before each invocation to ensure correct filtering per platform.
     *
     * @return a Tuple2 of (newManifestDigest, newManifestSize) for updating the parent index
     */
    protected AugmentedManifest augmentManifest(String imageName, String tag, Map<String,List<String>> headers, ManifestInfo manifestInfo) {
        // fetch the image config blob — this JSON contains the architecture field,
        // rootfs layer digests, and container config (env, entrypoint, etc.)
        final resp = client.getString("/v2/$imageName/blobs/$manifestInfo.configDigest", headers)
        checkResponseCode(resp, client.route, true)
        final imageConfig = resp.body() as String
        if( log.isTraceEnabled() )
            log.trace "Augment: image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"

        // read the architecture from the image config to determine which fusion layers to keep.
        // this is the authoritative source of the image's architecture — more reliable than
        // the platform field in the manifest index, which may be missing or incorrect.
        final configJson = new JsonSlurper().parseText(imageConfig) as Map
        final resolvedArch = configJson.architecture as String
        if( resolvedArch ) {
            // filter this.containerConfig.layers to only include:
            // - non-fusion layers (always kept, e.g. the wave launcher data layer)
            // - fusion layers matching this architecture (e.g. fusion-amd64.tar.gz for amd64)
            // fusion layers for other architectures are discarded
            final resolvedPlatform = ContainerPlatform.of("linux/${resolvedArch}")
            this.containerConfig = MultiPlatformBuildService.filterLayersForPlatform(this.containerConfig, resolvedPlatform)
            log.debug "Augment: filtered layers for arch=${resolvedArch}; remaining=${this.containerConfig?.layers?.size()}"
        }

        // update the image config: add layer tar digests to rootfs.diff_ids,
        // apply container config changes (env, entrypoint, cmd, workingDir),
        // then store the new config blob and return its digest
        final newConfigResult = updateImageConfig(imageName, imageConfig, manifestInfo.oci)
        final newConfigDigest = newConfigResult[0]
        final newConfigJson = newConfigResult[1]
        if( log.isTraceEnabled() )
            log.trace "Augment: new config digest: $newConfigDigest"

        // update the image manifest: append new layer blobs to the layers array,
        // update the config reference to point to the new config digest,
        // then store the new manifest and return its digest + size
        final newManifestResult = updateImageManifest(imageName, manifestInfo.imageManifest, newConfigDigest, newConfigJson.size(), manifestInfo.oci)
        return new AugmentedManifest(newManifestResult.v1, newManifestResult.v2)
    }

    protected ManifestInfo parseManifest(String manifest, String targetDigest) {
        final json = new JsonSlurper().parseText(manifest) as Map
        final media = json.mediaType as String
        return parseManifest(media, manifest, json, targetDigest)
    }

    protected ManifestInfo parseManifest(String media, String manifest, String targetDigest) {
        final json = new JsonSlurper().parseText(manifest) as Map
        return parseManifest(media, manifest, json, targetDigest)
    }

    protected ManifestInfo parseManifest(String media, String manifest, Map json, String targetDigest) {
        if( media==DOCKER_MANIFEST_V2_TYPE || media==OCI_IMAGE_MANIFEST_V1 ) {
            final ref = ManifestSpec.of(json)
            return new ManifestInfo(manifest, ref.config.digest, targetDigest, media==OCI_IMAGE_MANIFEST_V1, ref)
        }
        return null
    }

    protected ManifestInfo findImageManifestAndDigest(String manifest, String imageName, String tag, Map<String,List<String>> headers) {

        def json = new JsonSlurper().parseText(manifest) as Map
        // check the response mime, can be either
        // 1. application/vnd.docker.distribution.manifest.list.v2+json ==> image list
        // 2. application/vnd.docker.distribution.manifest.v2+json  ==> image manifest

        String targetDigest = null
        String media = json.mediaType
        if( media==DOCKER_IMAGE_INDEX_V2 || media==OCI_IMAGE_INDEX_V1 ) {
            // get target manifest
            final oci = media == OCI_IMAGE_INDEX_V1
            targetDigest = findTargetDigest(json, oci)
            final resp3 = client.getString("/v2/$imageName/manifests/$targetDigest", headers)
            manifest = resp3.body()
            if( log.isTraceEnabled() )
                log.trace("Image $imageName:$tag => image manifest=\n${JsonOutput.prettyPrint(manifest)}")
            // parse the new manifest
            json = new JsonSlurper().parseText(manifest) as Map
            media = json.mediaType
        }

        if( media==DOCKER_MANIFEST_V2_TYPE || media==OCI_IMAGE_MANIFEST_V1 ) {
            // find the image config digest
            final ref = ManifestSpec.of(json)
            return new ManifestInfo(manifest, ref.config.digest, targetDigest, media==OCI_IMAGE_MANIFEST_V1, ref)
        }
        else {
            throw new IllegalArgumentException("Unexpected media type for request '$imageName:$tag' - offending value: $media")
        }

    }

    /**
     * Rewrite the image index by replacing old platform manifest digests with new augmented ones.
     * Handles one or more digest replacements in a single pass, then stores the updated index.
     */
    protected String updateImageIndex(String imageName, String manifestsList, Map<String, AugmentedManifest> digestUpdates, boolean oci) {
        // parse the original index JSON
        final json = new JsonSlurper().parseText(manifestsList) as Map
        final list = json.manifests as List<Map>

        // replace each old digest with the new augmented digest and size
        for( Map.Entry<String, AugmentedManifest> update : digestUpdates.entrySet() ) {
            final entry = list.find( it -> it.digest == update.key )
            if( !entry )
                throw new IllegalStateException("Missing manifest entry for digest: ${update.key}")
            entry.digest = update.value.digest
            entry.size = update.value.size
        }

        final updated = JsonOutput.toJson(json)
        final result = RegHelper.digest(updated)
        final type = oci ? OCI_IMAGE_INDEX_V1 : DOCKER_IMAGE_INDEX_V2
        if( manifestsList == updated )
            throw new IllegalArgumentException("Unable to update image index - no entries were modified")
        final target = "$client.registry.name/v2/$imageName/manifests/$result"
        storage.saveManifest(target, updated, type, result)
        return result
    }

    protected Map layerBlob(String image, ContainerLayer layer) {
        log.debug "Adding layer: $layer to image: $client.registry.name/$image"
        // store the layer blob in the cache
        final String path = "$client.registry.name/v2/$image/blobs/$layer.gzipDigest"
        final store = storage.saveBlob(path, layer)

        final result = new LinkedHashMap(10)
        result."mediaType" = store.mediaType
        result."size" = store.size
        result."digest" = store.digest
        return result
    }


    protected Tuple2<String,Integer> updateImageManifest(String imageName, String imageManifest, String newImageConfigDigest, newImageConfigSize, boolean oci) {
        // turn the json string into a json map
        // and append the new layer
        final manifest = (Map) new JsonSlurper().parseText(imageManifest)
        final layers = (manifest.layers as List)

        for( ContainerLayer it : containerConfig.layers ) {
            // get the layer blob
            final newLayer= layerBlob(imageName, it)
            layers.add( newLayer )
        }

        // update the config digest
        final config = (manifest.config as Map)
        config.digest = newImageConfigDigest
        config.size = newImageConfigSize

        // turn the updated manifest into a json
        final newManifest = JsonOutput.toJson(manifest)

        // add to the cache
        final digest = RegHelper.digest(newManifest)
        final path = "$client.registry.name/v2/$imageName/manifests/$digest"
        final type = oci ? OCI_IMAGE_MANIFEST_V1 : DOCKER_MANIFEST_V2_TYPE
        final size = newManifest.size()
        storage.saveManifest(path, newManifest, type, digest)

        // return the updated image manifest digest
        return new Tuple2<String, Integer>(digest, size)
    }

    protected static String processEntryPoint(value) {
        if( !value )
            return null
        if( value instanceof List ) {
            if( value.size() == 1 )
                return value.get(0)
            return JacksonHelper.toJson(value)
        }
        if( value instanceof String )
            return value
        log.warn "Invalid Entrypoint type: ${value.getClass().getName()} -- Offending value: $value"
        return null
    }

    protected List<String> appendEnv(List<String> env, List<String> newEntries) {
        if( !newEntries )
            return env
        return env
                ? (env + newEntries)
                : newEntries
    }

    protected Map enrichConfig(Map config){
        final entryChain = processEntryPoint(config.Entrypoint)
        if( containerConfig.entrypoint ) {
            config.Entrypoint = containerConfig.entrypoint
        }
        if( containerConfig.cmd ) {
            config.Cmd = containerConfig.cmd
        }
        if( containerConfig.workingDir ) {
            config.WorkingDir = containerConfig.workingDir
        }
        if( containerConfig.env ) {
            config.Env = appendEnv(config.Env as List, containerConfig.env)
        }
        if( entryChain ) {
            config.Env = appendEnv( config.Env as List, [ "WAVE_ENTRY_CHAIN="+entryChain ] )
        }

        return config
    }

    protected List<String> updateImageConfig(String imageName, String imageConfig, boolean oci) {

        // turn the json string into a json map
        // and append the new layer
        final manifest = new JsonSlurper().parseText(imageConfig) as Map
        final rootfs = manifest.rootfs as Map
        final layers = rootfs.diff_ids as List

        for( ContainerLayer it : containerConfig.layers ) {
            layers.add( it.tarDigest )
        }

        // update the image config
        enrichConfig(manifest.config as Map)

        // turn the updated manifest into a json
        final newConfig = JsonOutput.toJson(manifest)

        // add to the cache
        final digest = RegHelper.digest(newConfig)
        final path = "$client.registry.name/v2/$imageName/blobs/$digest"
        final type = oci ? OCI_IMAGE_CONFIG_V1 : DOCKER_IMAGE_CONFIG_V1
        storage.saveBlob(path, newConfig.bytes, type, digest)

        // return the updated image manifest digest
        return List.of(digest, newConfig)
    }

    protected String findTargetDigest( String body, boolean oci ) {
        findTargetDigest((Map) new JsonSlurper().parseText(body), oci)
    }

    protected String findTargetDigest(Map json, boolean oci) {
        final record = (Map)json.manifests.find(oci ? this.&matchesOciManifest : this.&matchesDockerManifest)
        if( !record )
            throw new BadRequestException("Cannot find platform '${platform}' in the manifest: ${JsonOutput.toJson(json)}")
        final result = record.get('digest')
        if( !result )
            throw new BadRequestException("Cannot find digest entry for platform '${platform}' in the manifest: ${JsonOutput.toJson(json)}")
        if( log.isTraceEnabled() )
            log.trace "Find target digest platform: $platform ==> digest: $result"
        return result
    }

    protected boolean matchesDockerManifest(Map<String,String> record) {
        return record.mediaType == DOCKER_MANIFEST_V2_TYPE && matchesPlatformEntry(record.platform as Map)
    }

    protected boolean matchesOciManifest(Map<String,String> record) {
        return record.mediaType == OCI_IMAGE_MANIFEST_V1 && matchesPlatformEntry(record.platform as Map)
    }

    protected void rewriteHistoryV1( List<Map> history ){
        assert history.size()

        if( !containerConfig ) {
            // nothing to do
            return
        }

        final first = history.first()
        final topEntry = (Map) new JsonSlurper().parseText(first['v1Compatibility'].toString())

        def entry = new LinkedHashMap(topEntry)
        def parentId = topEntry.id
        final layers = containerConfig.layers ?: Collections.<ContainerLayer>emptyList()
        for( ContainerLayer it : layers ) {
            final now = Instant.now().toString()
            final id = RegHelper.stringToId(it.tarDigest)
            entry = new LinkedHashMap(10)
            entry.id = id
            entry.parent = parentId
            entry.created = now
            entry.container_config = [Cmd: ["\"/bin/sh -c #(nop) CMD [\"/bin/sh\"]"]]
            // create the new item
            history.add(0, Map.of('v1Compatibility', JsonOutput.toJson(entry)))
            // roll the parent id
            parentId = entry.id
        }

        // rewrite the top most history entry config
        enrichConfig(topEntry.config as Map)
        for( String it : topEntry.keySet() ) {
            // ignore the fields set previously
            if( it=='id' || it=='parent' || it=='created' )
                continue
            entry.put( it, topEntry.get(it) )
        }

        history.set(0, Map.of('v1Compatibility', JsonOutput.toJson(entry)))
    }

    protected void rewriteLayersV1(String imageName, List<Map> fsLayers){
        assert fsLayers.size()

        for( ContainerLayer it : containerConfig.layers ) {
            final blob = layerBlob(imageName, it)
            final newLayer= [blobSum: blob.digest]
            fsLayers.add(0, newLayer)
        }
    }

    protected void rewriteSignatureV1(Map manifest){
        def newManifestLength = JsonOutput.toJson(manifest).length()

        def signatures = manifest.signatures as List<Map>
        def signature = signatures.first()
        def signprotected = signature.protected as String

        def protecteddecoded = new JsonSlurper().parseText(new String(signprotected.decodeBase64())) as Map
        protecteddecoded.formatLength = newManifestLength-1

        def protectedBase64 = JsonOutput.toJson(protecteddecoded).bytes.encodeBase64().toString().replaceAll('=','')
        signature.protected = protectedBase64

    }

    String resolveV1Manifest(String body, String imageName){
        final manifest = new JsonSlurper().parseText(body) as Map

        def fsLayers = manifest.fsLayers as List<Map>
        def history = manifest.history as List<Map>

        rewriteHistoryV1(history)
        rewriteLayersV1(imageName, fsLayers)
        rewriteSignatureV1(manifest)

        final newManifestJson = JsonOutput.toJson(manifest)
        final newManifestDigest = RegHelper.digest(newManifestJson)
        final targetPath = "$client.registry.name/v2/$imageName/manifests/$newManifestDigest"
        storage.saveManifest(targetPath, newManifestJson, DOCKER_MANIFEST_V1_JWS_TYPE, newManifestDigest)
        return newManifestDigest
    }

    ContainerOrIndexSpec getContainerSpec(String imageName, String tag, Map<String,List<String>> headers) {
        assert client, "Missing client"

        // resolve image tag to digest
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        final digest = resp1.headers().firstValue('docker-content-digest').orElse(null)
        if( log.isTraceEnabled() )
            log.trace "Config (1): image $imageName:$tag => digest=$digest"
        checkResponseCode(resp1, client.route, false)

        // get manifest list for digest
        final resp2 = client.getString("/v2/$imageName/manifests/$digest", headers)
        final type = resp2.headers().firstValue('content-type').orElse(null)
        checkResponseCode(resp2, client.route, false)
        final manifestsList = resp2.body()
        if( log.isTraceEnabled() )
            log.trace "Config (2): image $imageName:$tag => type=$type; manifests list:\n${JsonOutput.prettyPrint(manifestsList)}"

        // check for legacy docker manifest type
        if( type==DOCKER_MANIFEST_V1_JWS_TYPE || type==DOCKER_MANIFEST_V1_TYPE ) {
            final json = new JsonSlurper().parseText(manifestsList) as Map
            final config = ConfigSpec.parseV1(json)
            final manifest = ManifestSpec.parseV1(json)
            final spec = new ContainerSpec(
                    client.registry.name,
                    client.registry.host.toString(),
                    imageName,
                    tag,
                    digest,
                    config,
                    manifest)
            return new ContainerOrIndexSpec(spec)
        }

        // when the target platform is not specific and it's a index media type
        // return the container index specification
        if( !platform && (type==DOCKER_IMAGE_INDEX_V2 || type==OCI_IMAGE_INDEX_V1) ) {
            final spec = IndexSpec
                    .parse(manifestsList)
                    .withDigest(digest)
            return new ContainerOrIndexSpec(spec)
        }

        final ManifestInfo manifestResult
                = parseManifest(type, manifestsList, digest)
                ?: findImageManifestAndDigest(manifestsList, imageName, tag, headers)

        // fetch the image config
        final resp5 = client.getString("/v2/$imageName/blobs/$manifestResult.configDigest", headers)
        checkResponseCode(resp5, client.route, true)
        final imageConfig = resp5.body()
        if( log.isTraceEnabled() )
            log.trace "Config (4): image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"

        final config = ConfigSpec.parse(imageConfig)
        final manifest = manifestResult.manifestSpec
        final spec = new ContainerSpec(
                client.registry.name,
                client.registry.host.toString(),
                imageName,
                tag,
                manifestResult.targetDigest,
                config,
                manifest)
        return new ContainerOrIndexSpec(spec)
    }
}
