package io.seqera.wave.core

import java.nio.file.Path

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.model.ContentType
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.model.LayerConfig
import io.seqera.wave.proxy.InvalidResponseException
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.storage.Storage
import io.seqera.wave.storage.reader.ContentReaderFactory
import io.seqera.wave.util.RegHelper

/**
 * Implement the logic of container manifest manipulation and
 * layers injections
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class ContainerScanner {

    private ProxyClient client
    private ContainerConfig containerConfig
    private String arch

    private Storage storage

    ContainerConfig getContainerConfig() {
        return containerConfig
    }

    ContainerScanner withStorage(Storage cache) {
        this.storage = cache
        return this
    }

    ContainerScanner withClient(ProxyClient client) {
        this.client = client
        return this
    }

    ContainerScanner withArch(String arch) {
        assert arch, "Missing 'arch' parameter"
        assert arch in ['x86_64', 'amd64', 'arm64'], "Unsupported architecture: $arch"

        this.arch = arch == 'x86_64' ? 'amd64' : arch
        return this
    }

    ContainerScanner withContainerConfig(ContainerConfig containerConfig) {
        this.containerConfig = containerConfig
        final l = containerConfig?.layers ?: Collections.<ContainerLayer>emptyList()
        for( ContainerLayer it : l )
            it.validate()
        return this
    }

    @Deprecated
    ContainerScanner withLayerConfig(Path configPath) {
        containerConfig = configPath ? LayerConfig.containerConfigAdapter(configPath) : null
        return this
    }

    String resolve(RoutePath route, Map<String,List<String>> headers) {
        assert route, "Missing route"
        if( route.request?.containerConfig )
            this.containerConfig = route.request.containerConfig
        return resolve(route.image, route.reference, headers)
    }

    String resolve(String imageName, String tag, Map<String,List<String>> headers) {
        assert client, "Missing client"
        assert storage, "Missing storage"
        assert arch, "Missing 'arch' parameter"

        // resolve image tag to digest
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        final digest = resp1.headers().firstValue('docker-content-digest').orElse(null)
        log.debug "Resolve (1): image $imageName:$tag => digest=$digest"
        if( resp1.statusCode() != 200 )
            throw new InvalidResponseException("Unexpected response statusCode: ${resp1.statusCode()}", resp1)

        // get manifest list for digest
        final resp2 = client.getString("/v2/$imageName/manifests/$digest", headers)
        final type = resp2.headers().firstValue('content-type').orElse(null)
        if( resp2.statusCode() != 200 )
            throw new InvalidResponseException("Unexpected response statusCode: ${resp2.statusCode()}", resp2)
        final manifestsList = resp2.body()
        log.debug "Resolve (2): image $imageName:$tag => type=$type; manifests list:\n${JsonOutput.prettyPrint(manifestsList)}"

        // when there's no container config, not much to do
        // just cache the manifest content and return the digest
        if( !containerConfig ) {
            log.debug "Resolve (3): container config provided for image=$imageName:$tag"
            storage.saveManifest("/v2/$imageName/manifests/$digest", manifestsList, type, digest)
            return digest
        }

        if( type == ContentType.DOCKER_MANIFEST_V1_JWS_TYPE ) {
            final v1Digest = resolveV1Manifest(manifestsList, imageName)
            log.debug "Resolve (4) ==> new manifest v1 digest: $v1Digest"
            return v1Digest
        }

        final manifestResult = findImageManifestAndDigest(manifestsList, imageName, tag, headers)
        final imageManifest = manifestResult.first
        final configDigest = manifestResult.second
        final targetDigest = manifestResult.third

        // fetch the image config
        final resp4 = client.getString("/v2/$imageName/blobs/$configDigest", headers)
        final imageConfig = resp4.body()
        log.debug "Resolve (5): image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"

        // update the image config adding the new layer
        final newConfigDigest = updateImageConfig(imageName, imageConfig)
        log.debug "Resolve (6) ==> new config digest: $newConfigDigest"

        // update the image manifest adding a new layer
        // returns the updated image manifest digest
        final newManifestDigest = updateImageManifest(imageName, imageManifest, newConfigDigest)
        log.debug "Resolve (7) ==> new image digest: $newManifestDigest"

        if( !targetDigest ) {
            return newManifestDigest
        }
        else {
            // update the manifests list with the new digest
            // returns the manifests list digest
            final newListDigest = updateManifestsList(imageName, manifestsList, targetDigest, newManifestDigest)
            log.debug "Resolve (8) ==> new list digest: $newListDigest"
            return newListDigest
        }

    }

    protected Tuple3<String,String,String> findImageManifestAndDigest(String manifest, String imageName, String tag, Map<String,List<String>> headers) {

        def json = new JsonSlurper().parseText(manifest) as Map
        // check the response mime, can be either
        // 1. application/vnd.docker.distribution.manifest.list.v2+json ==> image list
        // 2. application/vnd.docker.distribution.manifest.v2+json  ==> image manifest

        def targetDigest = null
        def media = json.mediaType
        if( media == ContentType.DOCKER_MANIFEST_LIST_V2 ) {
            // get target manifest
            targetDigest = findTargetDigest(json)
            final resp3 = client.getString("/v2/$imageName/manifests/$targetDigest", headers)
            manifest = resp3.body()
            log.debug "Image $imageName:$tag => image manifest=\n${JsonOutput.prettyPrint(manifest)}"
            // parse the new manifest
            json = new JsonSlurper().parseText(manifest) as Map
            media = json.mediaType
        }

        if( media == ContentType.DOCKER_MANIFEST_V2_TYPE ) {
            // find the image config digest
            final configDigest = findImageConfigDigest(manifest)
            return new Tuple3(manifest, configDigest, targetDigest)
        }
        else {
            throw new IllegalArgumentException("Unexpected media type for request '$imageName:$tag' - offending value: $media")
        }

    }

    protected String updateManifestsList(String imageName, String manifestsList, String targetDigest, String newDigest) {
        final updated = manifestsList.replace(targetDigest, newDigest)
        final result = RegHelper.digest(updated)
        final type = ContentType.DOCKER_MANIFEST_LIST_V2
        // make sure the manifest was updated
        if( manifestsList==updated )
            throw new IllegalArgumentException("Unable to find target digest '$targetDigest' into image list manifest")
        // store in the cache
        storage.saveManifest("/v2/$imageName/manifests/$result", updated, type, result)
        // return the updated manifests list digest
        return result
    }

    synchronized protected Map layerBlob(String image, ContainerLayer layer) {
        log.debug "Adding layer: $layer to image: $image"
        // store the layer blob in the cache
        final type = "application/vnd.docker.image.rootfs.diff.tar.gzip"
        final location = layer.location
        final digest = layer.gzipDigest
        final size = layer.gzipSize
        final String path = "/v2/$image/blobs/$digest"
        final content = ContentReaderFactory.of(location)
        storage.saveBlob(path, content, type, digest)

        final result = new HashMap(10)
        result."mediaType" = type
        result."size" = size
        result."digest" = digest
        return result
    }

    /**
     * @param imageManifest hold the image config json. It has the following structure
     * <pre>
     *     {
     *      "schemaVersion": 2,
     *      "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
     *      "config": {
     *              "mediaType": "application/vnd.docker.container.image.v1+json",
     *              "size": 1469,
     *              "digest": "sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412"
     *          },
     *      "layers": [
     *          {
     *              "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
     *              "size": 2479,
     *              "digest": "sha256:2db29710123e3e53a794f2694094b9b4338aa9ee5c40b930cb8063a1be392c54"
     *          }
     *      ]
     *    }
     * </pre>
     * @return
     */
    @CompileDynamic
    protected String findImageConfigDigest(String imageManifest) {
        final json = (Map) new JsonSlurper().parseText(imageManifest)
        return json.config.digest
    }

    protected String updateImageManifest(String imageName, String imageManifest, String newImageConfigDigest) {

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
        (manifest.config as Map).digest = newImageConfigDigest

        // turn the updated manifest into a json
        final newManifest = JsonOutput.prettyPrint(JsonOutput.toJson(manifest))

        // add to the cache
        final digest = RegHelper.digest(newManifest)
        final path = "/v2/$imageName/manifests/$digest"
        storage.saveManifest(path, newManifest, ContentType.DOCKER_MANIFEST_V2_TYPE, digest)

        // return the updated image manifest digest
        return digest
    }

    protected String getFirst(value) {
        if( !value )
            return null
        if( value instanceof List ) {
            if( value.size()>1 ) log.warn "Invalid  Entrypoint value: $value -- Only the first array element will be taken"
            return value.get(0)
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
        final entryChain = getFirst(config.Entrypoint)
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

        config
    }

    protected String updateImageConfig(String imageName, String imageConfig) {

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
        final path = "/v2/$imageName/blobs/$digest"
        storage.saveBlob(path, newConfig.bytes, ContentType.DOCKER_IMAGE_V1, digest)

        // return the updated image manifest digest
        return digest
    }


    protected String findTargetDigest(String body ) {
        findTargetDigest((Map) new JsonSlurper().parseText(body))
    }

    @CompileDynamic
    protected String findTargetDigest(Map json) {
        final mediaType = ContentType.DOCKER_MANIFEST_V2_TYPE
        final record = json.manifests.find( { record ->  record.mediaType == mediaType && record.platform.os=='linux' && record.platform.architecture==arch } )
        final result = record.digest
        log.debug "Find target digest arch: $arch ==> digest: $result"
        return result
    }

    protected void rewriteHistoryV1( List<Map> history){
        assert history.size()

        def first = history.first()

        def newHistoryId = RegHelper.stringToId(JsonOutput.toJson(history))
        def newV1Compatibility = new JsonSlurper().parseText(first['v1Compatibility'].toString()) as Map
        newV1Compatibility.parent = newV1Compatibility.id
        newV1Compatibility.id = newHistoryId

        // update the image config
        enrichConfig(newV1Compatibility.config as Map)

        // create the new item
        def newHistoryItem = [
                v1Compatibility: JsonOutput.toJson(newV1Compatibility)
        ]
        history.add(0, newHistoryItem)
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

        def newManifestJson = JsonOutput.toJson(manifest)
        def newManifestDigest = RegHelper.digest(newManifestJson)

        storage.saveManifest("/v2/$imageName/manifests/$newManifestDigest", newManifestJson, ContentType.DOCKER_MANIFEST_V1_JWS_TYPE, newManifestDigest)
        return newManifestDigest
    }
}
