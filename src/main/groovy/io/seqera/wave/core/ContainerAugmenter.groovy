package io.seqera.wave.core

import java.net.http.HttpResponse
import java.time.Instant

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpStatus
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.exception.DockerRegistryException
import io.seqera.wave.model.ContentType
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.storage.Storage
import io.seqera.wave.storage.reader.ContentReaderFactory
import io.seqera.wave.util.RegHelper

import static io.seqera.wave.model.ContentType.*

/**
 * Implement the logic of container manifest manipulation and
 * layers injections
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class ContainerAugmenter {

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
        if( route.request?.containerConfig )
            this.containerConfig = route.request.containerConfig
        return resolve(route.image, route.reference, headers)
    }

    protected void checkResponseCode(HttpResponse<?> response, RoutePath route, boolean blob) {
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
            log.debug("Unexpected response code ${code} on ${response.uri()}")
        }
    }

    ContainerDigestPair resolve(String imageName, String tag, Map<String,List<String>> headers) {
        assert client, "Missing client"
        assert storage, "Missing storage"
        assert platform, "Missing 'platform' parameter"

        // resolve image tag to digest
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        final digest = resp1.headers().firstValue('docker-content-digest').orElse(null)
        log.trace "Resolve (1): image $imageName:$tag => digest=$digest"
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
            storage.saveManifest("/v2/$imageName/manifests/$digest", manifestsList, type, digest)
            return new ContainerDigestPair(digest,digest)
        }

        if( tag.startsWith('sha256:')) {
            // container using a digest as tag cannot be augmented because it would
            // require to alter the digest itself
            final msg = "Operation not allowed for container '$imageName@$tag'"
            throw new DockerRegistryException(msg, 400, 'UNSUPPORTED')
        }

        if( type == ContentType.DOCKER_MANIFEST_V1_JWS_TYPE ) {
            final v1Digest = resolveV1Manifest(manifestsList, imageName)
            if( log.isTraceEnabled() ) {
                final v1Manifest = storage.getManifest("/v2/$imageName/manifests/$v1Digest").orElse(null)
                log.trace "Resolve (4) ==> new manifest v1 digest: $v1Digest\n${JsonOutput.prettyPrint(new String(v1Manifest.bytes))}"
            }
            return new ContainerDigestPair(digest, v1Digest)
        }

        final manifestResult = findImageManifestAndDigest(manifestsList, imageName, tag, headers)
        final imageManifest = manifestResult.first
        final configDigest = manifestResult.second
        final targetDigest = manifestResult.third
        final oci = manifestResult.fourth

        // fetch the image config
        final resp5 = client.getString("/v2/$imageName/blobs/$configDigest", headers)
        checkResponseCode(resp5, client.route, true)
        final imageConfig = resp5.body()
        log.trace "Resolve (5): image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"

        // update the image config adding the new layer
        final newConfigResult = updateImageConfig(imageName, imageConfig, oci)
        final newConfigDigest = newConfigResult[0]
        final newConfigJson = newConfigResult[1]
        log.trace "Resolve (6) ==> new config digest: $newConfigDigest => new config=\n${JsonOutput.prettyPrint(newConfigJson)} "

        // update the image manifest adding a new layer
        // returns the updated image manifest digest
        final newManifestDigest = updateImageManifest(imageName, imageManifest, newConfigDigest, newConfigJson.size(), oci)
        log.trace "Resolve (7) ==> new image digest: $newManifestDigest"

        if( !targetDigest ) {
            return new ContainerDigestPair(digest, newManifestDigest)
        }
        else {
            // update the manifests list with the new digest
            // returns the manifests list digest
            final newListDigest = updateImageIndex(imageName, manifestsList, targetDigest, newManifestDigest, oci)
            log.trace "Resolve (8) ==> new list digest: $newListDigest"
            return new ContainerDigestPair(digest, newListDigest)
        }

    }

    protected Tuple4<String,String,String,Boolean> findImageManifestAndDigest(String manifest, String imageName, String tag, Map<String,List<String>> headers) {

        def json = new JsonSlurper().parseText(manifest) as Map
        // check the response mime, can be either
        // 1. application/vnd.docker.distribution.manifest.list.v2+json ==> image list
        // 2. application/vnd.docker.distribution.manifest.v2+json  ==> image manifest

        def targetDigest = null
        def media = json.mediaType
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
            final configDigest = findImageConfigDigest(manifest)
            return new Tuple4(manifest, configDigest, targetDigest, media==OCI_IMAGE_MANIFEST_V1)
        }
        else {
            throw new IllegalArgumentException("Unexpected media type for request '$imageName:$tag' - offending value: $media")
        }

    }

    protected String updateImageIndex(String imageName, String manifestsList, String targetDigest, String newDigest, boolean oci) {
        final updated = manifestsList.replace(targetDigest, newDigest)
        final result = RegHelper.digest(updated)
        final type = oci ? OCI_IMAGE_INDEX_V1 : DOCKER_IMAGE_INDEX_V2
        // make sure the manifest was updated
        if( manifestsList==updated )
            throw new IllegalArgumentException("Unable to find target digest '$targetDigest' into image index")
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

    protected String updateImageManifest(String imageName, String imageManifest, String newImageConfigDigest, newImageConfigSize, boolean oci) {

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
        final newManifest = JsonOutput.prettyPrint(JsonOutput.toJson(manifest))

        // add to the cache
        final digest = RegHelper.digest(newManifest)
        final path = "/v2/$imageName/manifests/$digest"
        final type = oci ? OCI_IMAGE_MANIFEST_V1 : DOCKER_MANIFEST_V2_TYPE
        storage.saveManifest(path, newManifest, type, digest)

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
        final path = "/v2/$imageName/blobs/$digest"
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
        final result = record.get('digest')
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

        def newManifestJson = JsonOutput.toJson(manifest)
        def newManifestDigest = RegHelper.digest(newManifestJson)

        storage.saveManifest("/v2/$imageName/manifests/$newManifestDigest", newManifestJson, ContentType.DOCKER_MANIFEST_V1_JWS_TYPE, newManifestDigest)
        return newManifestDigest
    }
}
