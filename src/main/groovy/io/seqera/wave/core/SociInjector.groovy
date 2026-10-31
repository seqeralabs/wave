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

package io.seqera.wave.core

import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.service.soci.SociDocs
import io.seqera.wave.service.soci.SociIndexService
import io.seqera.wave.service.soci.SociManifestAssembler
import io.seqera.wave.service.soci.SociPlatforms
import io.seqera.wave.storage.Storage
import io.seqera.wave.tower.PlatformId
import static io.seqera.wave.model.ContentType.DOCKER_IMAGE_INDEX_V2
import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V2_TYPE
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1
/**
 * Make a container resolved by the {@link ContainerAugmenter} SOCI-enabled, by adding the
 * SOCI index manifest v2 to its image index, when the zTOCs of its layers are available.
 *
 * When they are not, the container is served as it is and the indexing of the missing layers is
 * requested, so that a later pull is SOCI-enabled. The documents are saved in the {@link Storage},
 * the zTOC blobs are served from the zTOC repository.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class SociInjector {

    enum State { READY, PENDING, NONE }

    @Canonical
    static class Docs {
        final State state
        final SociDocs docs
    }

    private ProxyClient client
    private Storage storage
    private SociIndexService service
    private SociConfig config
    private PlatformId identity
    private ContainerPlatform platform
    /** layers added by Wave (e.g. Fusion), which are not in the upstream repository and therefore not indexed */
    private Set<String> excludedLayers

    SociInjector(ProxyClient client, Storage storage, SociIndexService service, SociConfig config, PlatformId identity, ContainerPlatform platform, Set<String> excludedLayers) {
        this.client = client
        this.storage = storage
        this.service = service
        this.config = config
        this.identity = identity
        this.platform = platform
        this.excludedLayers = excludedLayers ?: Set.<String>of()
    }

    /**
     * Add the SOCI documents to a resolved container
     *
     * @param imageName The image name e.g. {@code library/ubuntu}
     * @param pair The digests of the resolved container
     * @param headers The request headers, used for upstream requests
     * @return The digests of the SOCI-enabled container, or the given ones when the container cannot be SOCI-enabled (yet)
     */
    ContainerDigestPair apply(String imageName, ContainerDigestPair pair, Map<String,List<String>> headers) {
        try {
            return apply0(imageName, pair, headers)
        }
        catch (Exception e) {
            // SOCI is an optional enhancement, serve the container as it is
            log.warn "Unable to apply SOCI index to image ${client.registry.name}/${imageName} - cause: ${e.message}", e
            return pair
        }
    }

    protected ContainerDigestPair apply0(String imageName, ContainerDigestPair pair, Map<String,List<String>> headers) {
        final content = loadManifest(imageName, pair.target, headers)
        if( content==null )
            return pair
        final json = new JsonSlurper().parseText(content) as Map
        final media = json.mediaType as String
        if( media==OCI_IMAGE_INDEX_V1 || media==DOCKER_IMAGE_INDEX_V2 )
            return applyIndex(imageName, pair, content, json, headers)
        if( media==OCI_IMAGE_MANIFEST_V1 || media==DOCKER_MANIFEST_V2_TYPE )
            return applyManifest(imageName, pair, content, json, headers)
        return pair
    }

    protected ContainerDigestPair applyIndex(String imageName, ContainerDigestPair pair, String content, Map json, Map<String,List<String>> headers) {
        if( SociManifestAssembler.hasSociIndex(content) )
            return pair
        final ready = new LinkedHashMap<String,SociDocs>()
        boolean pending = false
        for( Map entry : SociPlatforms.targetEntries(json, platform) ) {
            final manifest = loadManifest(imageName, entry.digest as String, headers)
            final docs = manifest!=null ? docsFor(imageName, manifest) : new Docs(State.NONE, null)
            if( docs.state==State.PENDING )
                pending = true
            else if( docs.state==State.READY )
                ready.put(entry.digest as String, docs.docs)
        }
        if( pending || !ready )
            return new ContainerDigestPair(pair.source, pair.target, pending)
        final index = SociManifestAssembler.updateIndex(content, ready)
        return new ContainerDigestPair(pair.source, saveIndex(imageName, index), false)
    }

    protected ContainerDigestPair applyManifest(String imageName, ContainerDigestPair pair, String content, Map json, Map<String,List<String>> headers) {
        final docs = docsFor(imageName, content)
        if( docs.state!=State.READY )
            return new ContainerDigestPair(pair.source, pair.target, docs.state==State.PENDING)
        final configDigest = (json.config as Map).digest as String
        final index = SociManifestAssembler.wrapInIndex(docs.docs, imagePlatform(imageName, configDigest, headers))
        return new ContainerDigestPair(pair.source, saveIndex(imageName, index), false)
    }

    /**
     * Create the SOCI documents of an image manifest when the zTOCs of all its eligible layers are available,
     * otherwise request the indexing of the missing ones
     */
    protected Docs docsFor(String imageName, String manifest) {
        final layers = SociManifestAssembler
                .eligibleLayers(manifest, config.minLayerSize)
                .findAll((Map it) -> !excludedLayers.contains(it.digest as String))
        if( !layers )
            return new Docs(State.NONE, null)
        final ztocs = service.findZtocs(layers.collect((Map it) -> it.digest as String))
        final missing = layers.findAll((Map it) -> !ztocs.containsKey(it.digest as String))
        if( missing ) {
            log.debug "SOCI index pending for image ${client.registry.name}/${imageName} - indexed ${ztocs.size()} of ${layers.size()} layers"
            service.indexLayers("${client.registry.name}/${imageName}".toString(), missing, identity)
            return new Docs(State.PENDING, null)
        }
        final docs = SociManifestAssembler.assemble(manifest, ztocs, config.spanSize)
        saveDocs(imageName, docs)
        return new Docs(State.READY, docs)
    }

    protected void saveDocs(String imageName, SociDocs docs) {
        storage.saveManifest(manifestPath(imageName, docs.manifestDigest), docs.manifestJson, OCI_IMAGE_MANIFEST_V1, docs.manifestDigest)
        storage.saveManifest(manifestPath(imageName, docs.sociIndexDigest), docs.sociIndexJson, OCI_IMAGE_MANIFEST_V1, docs.sociIndexDigest)
        storage.saveBlob(blobPath(imageName, docs.configDigest), docs.configJson.bytes, SociManifestAssembler.SOCI_INDEX_ARTIFACT_TYPE, docs.configDigest)
        for( def it : docs.ztocs ) {
            final layer = new ContainerLayer(ztocLocation(it.ztocDigest), it.ztocDigest, it.ztocSize as Integer)
            storage.saveBlob(blobPath(imageName, it.ztocDigest), layer)
        }
    }

    /**
     * The location of a zTOC blob in the zTOC repository e.g. {@code docker://registry.io/v2/wave/soci-ztoc/blobs/sha256:...}
     */
    protected String ztocLocation(String digest) {
        final p = config.repo.indexOf('/')
        final registry = config.repo.substring(0, p)
        final repository = config.repo.substring(p+1)
        return "docker://${registry}/v2/${repository}/blobs/${digest}"
    }

    protected String saveIndex(String imageName, String index) {
        final digest = io.seqera.wave.util.RegHelper.digest(index)
        storage.saveManifest(manifestPath(imageName, digest), index, OCI_IMAGE_INDEX_V1, digest)
        return digest
    }

    protected Map imagePlatform(String imageName, String configDigest, Map<String,List<String>> headers) {
        return SociPlatforms.platformOf(loadBlob(imageName, configDigest, headers), platform)
    }

    /**
     * Load a manifest saved by Wave (augmented or cached) or fetch it from the upstream registry
     */
    protected String loadManifest(String imageName, String digest, Map<String,List<String>> headers) {
        final store = storage.getManifest(manifestPath(imageName, digest)).orElse(null)
        if( store )
            return new String(store.bytes)
        final resp = client.getString("/v2/${imageName}/manifests/${digest}", headers)
        return resp.statusCode()==200 ? resp.body() : null
    }

    protected String loadBlob(String imageName, String digest, Map<String,List<String>> headers) {
        final store = storage.getBlob(blobPath(imageName, digest)).orElse(null)
        if( store )
            return new String(store.bytes)
        final resp = client.getString("/v2/${imageName}/blobs/${digest}", headers)
        return resp.statusCode()==200 ? resp.body() : null
    }

    protected String manifestPath(String imageName, String digest) {
        return "${client.registry.name}/v2/${imageName}/manifests/${digest}"
    }

    protected String blobPath(String imageName, String digest) {
        return "${client.registry.name}/v2/${imageName}/blobs/${digest}"
    }
}
