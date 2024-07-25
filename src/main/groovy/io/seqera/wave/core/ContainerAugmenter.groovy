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
import io.seqera.wave.core.spec.ManifestSpec
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.storage.Storage
import io.seqera.wave.util.JacksonHelper
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
    
    private ProxyClient client
    private ContainerConfig containerConfig
    private ContainerPlatform platform = ContainerPlatform.DEFAULT
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
        this.platform = ContainerPlatform.of(value)
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
        if( route.request?.platform )
            this.platform = route.request.platform
        // note: do not propagate container config when "freeze" mode is enabled, because it has been already
        // applied during the container build phase, and therefore it should be ignored by the augmenter
        if( route.request?.containerConfig && !route.request.freeze )
            this.containerConfig = route.request.containerConfig
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
        log.trace "Resolve (1): image $imageName:$tag => digest=$digest; response code=${resp1.statusCode()}"
        checkResponseCode(resp1, client.route, false)

        // get manifest list for digest
        final resp2 = client.getString("/v2/$imageName/manifests/$digest", headers)
        final type = resp2.headers().firstValue('content-type').orElse(null)
        checkResponseCode(resp2, client.route, false)
        final manifestsList = resp2.body()
        log.trace "Resolve (2): image $imageName:$tag => type=$type; manifests list:\n${JsonOutput.prettyPrint(manifestsList)}"

        // when there's no container config, not much to do
        // just cache the manifest content and return the digest
        if( !containerConfig ) {
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

        final manifestResult = findImageManifestAndDigest(manifestsList, imageName, tag, headers)

        // fetch the image config
        final resp5 = client.getString("/v2/$imageName/blobs/$manifestResult.configDigest", headers)
        checkResponseCode(resp5, client.route, true)
        final imageConfig = resp5.body()
        log.trace "Resolve (5): image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"

        // update the image config adding the new layer
        final newConfigResult = updateImageConfig(imageName, imageConfig, manifestResult.oci)
        final newConfigDigest = newConfigResult[0]
        final newConfigJson = newConfigResult[1]
        log.trace "Resolve (6) ==> new config digest: $newConfigDigest => new config=\n${JsonOutput.prettyPrint(newConfigJson)} "

        // update the image manifest adding a new layer
        // returns the updated image manifest digest
        final newManifestResult = updateImageManifest(imageName, manifestResult.imageManifest, newConfigDigest, newConfigJson.size(), manifestResult.oci)
        final newManifestDigest = newManifestResult.v1
        final newManifestSize = newManifestResult.v2
        log.trace "Resolve (7) ==> new image digest: $newManifestDigest"

        if( !manifestResult.targetDigest ) {
            return new ContainerDigestPair(digest, newManifestDigest)
        }
        else {
            // update the manifests list with the new digest
            // returns the manifests list digest
            final newListDigest = updateImageIndex(imageName, manifestsList, manifestResult.targetDigest, newManifestDigest, newManifestSize, manifestResult.oci)
            log.trace "Resolve (8) ==> new list digest: $newListDigest"
            return new ContainerDigestPair(digest, newListDigest)
        }

    }

    protected ManifestInfo parseManifest(String media, String manifest, String targetDigest) {
        if( media==DOCKER_MANIFEST_V2_TYPE || media==OCI_IMAGE_MANIFEST_V1 ) {
            final json = new JsonSlurper().parseText(manifest) as Map
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

    protected String updateImageIndex(String imageName, String manifestsList, String targetDigest, String newDigest, Integer newSize, boolean oci) {
        final json = new JsonSlurper().parseText(manifestsList) as Map
        final list = json.manifests as List<Map>
        final entry = list.find( it -> it.digest==targetDigest )
        if( !entry )
            throw new IllegalStateException("Missing manifest entry for digest: $targetDigest")
        // update the target entry digest and size
        entry.digest = newDigest
        entry.size = newSize
        // serialize to json again  
        final updated = JsonOutput.toJson(json)
        final result = RegHelper.digest(updated)
        final type = oci ? OCI_IMAGE_INDEX_V1 : DOCKER_IMAGE_INDEX_V2
        // make sure the manifest was updated
        if( manifestsList==updated )
            throw new IllegalArgumentException("Unable to find target digest '$targetDigest' into image index")
        // store in the cache
        final target = "$client.registry.name/v2/$imageName/manifests/$result"
        storage.saveManifest(target, updated, type, result)
        // return the updated manifests list digest
        return result
    }

    synchronized protected Map layerBlob(String image, ContainerLayer layer) {
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
            throw new BadRequestException("Cannot find platform '${platform}' in the manifest:\n${JsonOutput.prettyPrint(JsonOutput.toJson(json))}")
        final result = record.get('digest')
        if( !result )
            throw new BadRequestException("Cannot find digest entry for platform '${platform}' in the manifest:\n${JsonOutput.prettyPrint(JsonOutput.toJson(json))}")
        log.trace "Find target digest platform: $platform ==> digest: $result"
        return result
    }

    protected boolean matchesDockerManifest(Map<String,String> record) {
        return record.mediaType == DOCKER_MANIFEST_V2_TYPE && platform.matches(record.platform as Map)
    }

    protected boolean matchesOciManifest(Map<String,String> record) {
        return record.mediaType == OCI_IMAGE_MANIFEST_V1 && platform.matches(record.platform as Map)
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

    ContainerSpec getContainerSpec(String imageName, String tag, Map<String,List<String>> headers) {
        assert client, "Missing client"
        assert platform, "Missing 'platform' parameter"

        // resolve image tag to digest
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        final digest = resp1.headers().firstValue('docker-content-digest').orElse(null)
        log.trace "Config (1): image $imageName:$tag => digest=$digest"
        checkResponseCode(resp1, client.route, false)

        // get manifest list for digest
        final resp2 = client.getString("/v2/$imageName/manifests/$digest", headers)
        final type = resp2.headers().firstValue('content-type').orElse(null)
        checkResponseCode(resp2, client.route, false)
        final manifestsList = resp2.body()
        log.trace "Config (2): image $imageName:$tag => type=$type; manifests list:\n${JsonOutput.prettyPrint(manifestsList)}"

        if( type==DOCKER_MANIFEST_V1_JWS_TYPE || type==DOCKER_MANIFEST_V1_TYPE ) {
            final json = new JsonSlurper().parseText(manifestsList) as Map
            final config = ConfigSpec.parseV1(json)
            final manifest = ManifestSpec.parseV1(json)
            return new ContainerSpec(
                    client.registry.name,
                    client.registry.host.toString(),
                    imageName,
                    tag,
                    digest,
                    config,
                    manifest )
        }

        final manifestResult
                = parseManifest(type, manifestsList,digest)
                ?: findImageManifestAndDigest(manifestsList, imageName, tag, headers)

        // fetch the image config
        final resp5 = client.getString("/v2/$imageName/blobs/$manifestResult.configDigest", headers)
        checkResponseCode(resp5, client.route, true)
        final imageConfig = resp5.body()
        log.trace "Config (4): image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"

        final config = ConfigSpec.parse(imageConfig)
        final manifest = manifestResult.manifestSpec
        return new ContainerSpec(
                client.registry.name,
                client.registry.host.toString(),
                imageName,
                tag,
                digest,
                config,
                manifest )
    }
}
