package io.seqera.docker

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import io.seqera.cache.Cache
import io.seqera.model.ContentType
import io.seqera.model.LayerConfig
import io.seqera.proxy.InvalidResponseException
import io.seqera.proxy.ProxyClient
import io.seqera.util.RegHelper

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class ContainerScanner {

    private ProxyClient client
    private Path layerConfigPath
    private String arch

    private Cache cache

    {
        withLayerConfig(Paths.get('pack/layers/layer.json'))
    }

    ContainerScanner withCache(Cache cache) {
        this.cache = cache
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

    ContainerScanner withLayerConfig(Path path) {
        log.debug "Layer config path: $path"
        this.layerConfigPath = path
        if( !Files.exists(path )) {
            throw new IllegalArgumentException("Specific config path does not exist: $path")
        }
        return this
    }

    Path getLayerConfigPath() {
        return layerConfigPath
    }

    protected LayerConfig getLayerConfig() {
        assert layerConfigPath, "Missing layer config path"
        final attrs = Files.readAttributes(layerConfigPath, BasicFileAttributes)
        // note file size and last modified timestamp are only needed
        // to invalidate the memoize cache
        return createConfig(layerConfigPath.toFile(), attrs.size(), attrs.lastModifiedTime().toMillis())
    }

    @Memoized
    protected LayerConfig createConfig(File path, long size, long lastModified) {
        final layerConfig = new JsonSlurper().parse(path) as LayerConfig
        if( !layerConfig.append?.location )
            throw new IllegalArgumentException("Missing layer tar path")
        if( !layerConfig.append?.gzipDigest )
            throw new IllegalArgumentException("Missing layer gzip digest")
        if( !layerConfig.append?.tarDigest )
            throw new IllegalArgumentException("Missing layer tar digest")

        if( !layerConfig.append.gzipDigest.startsWith('sha256:') )
            throw new IllegalArgumentException("Missing layer gzip digest should start with the 'sha256:' prefix -- offending value: $layerConfig.append.gzipDigest")
        if( !layerConfig.append.tarDigest.startsWith('sha256:') )
            throw new IllegalArgumentException("Missing layer tar digest should start with the 'sha256:' prefix -- offending value: $layerConfig.append.tarDigest")

        if( !Files.exists(layerConfig.append.locationPath) )
            throw new IllegalArgumentException("Missing layer tar file: $layerConfig.append.locationPath")

        log.debug "Layer info: path=$layerConfig.append.location; gzip-checksum=$layerConfig.append.gzipDigest; gzip-size: $layerConfig.append.gzipSize; tar-checksum=$layerConfig.append.tarDigest"
        return layerConfig
    }

    String resolve(String imageName, String tag, Map<String,List<String>> headers) {
        assert client, "Missing client"
        // resolve image tag to digest
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        final digest = resp1.headers().firstValue('docker-content-digest')
        log.debug "Image $imageName:$tag => digest=$digest"
        if( resp1.statusCode() != 200 )
            throw new InvalidResponseException("Unexpected response statusCode: ${resp1.statusCode()}", resp1)

        // get manifest list for digest
        final resp2 = client.getString("/v2/$imageName/manifests/${digest.get()}", headers)
        final type = resp2.headers().firstValue('content-type').orElse(null)
        if( resp2.statusCode() != 200 )
            throw new InvalidResponseException("Unexpected response statusCode: ${resp1.statusCode()}", resp1)
        final manifestsList = resp2.body()
        log.debug "Image $imageName:$tag => type=$type; manifests list:\n${JsonOutput.prettyPrint(manifestsList)}"

        if( type == ContentType.DOCKER_MANIFEST_V1_JWS_TYPE ) {
            final v1Digest = resolveV1Manifest(manifestsList, imageName)
            log.debug "==> new manifest v1 digest: $v1Digest"
            return v1Digest
        }

        final manifestResult = findImageManifestAndDigest(manifestsList, imageName, tag, headers)
        final imageManifest = manifestResult.first
        final configDigest = manifestResult.second
        final targetDigest = manifestResult.third

        // fetch the image config
        final resp4 = client.getString("/v2/$imageName/blobs/$configDigest", headers)
        final imageConfig = resp4.body()
        log.debug "Image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"

        // update the image config adding the new layer
        final newConfigDigest = updateImageConfig(imageName, imageConfig)
        log.debug "==> new config digest: $newConfigDigest"

        // update the image manifest adding a new layer
        // returns the updated image manifest digest
        final newManifestDigest = updateImageManifest(imageName, imageManifest, newConfigDigest)
        log.debug "==> new image digest: $newManifestDigest"

        if( !targetDigest ) {
            return newManifestDigest
        }
        else {
            // update the manifests list with the new digest
            // returns the manifests list digest
            final newListDigest = updateManifestsList(imageName, manifestsList, targetDigest, newManifestDigest)
            log.debug "==> new list digest: $newListDigest"

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
        cache.put("/v2/$imageName/manifests/$result", updated.bytes, type, result)
        // return the updated manifests list digest
        return result
    }

    @Memoized
    synchronized protected Map layerBlob(String image) {
        // store the layer blob in the cache
        final type = "application/vnd.docker.image.rootfs.diff.tar.gzip"
        final location = layerConfig.append.locationPath
        final buffer = Files.readAllBytes(location)
        final computed = RegHelper.digest(buffer)
        final digest = layerConfig.append.gzipDigest
        final size = layerConfig.append.gzipSize
        if( !buffer )
            throw new IllegalArgumentException("Layer content is empty -- path: $layerConfig.append.locationPath; exists: ${Files.exists(location)}")
        if( !size )
            throw new IllegalArgumentException("Layer size cannot be null or zero")
        if( digest != computed )
            log.warn("Layer gzip computed digest does not match with expected digest -- path=$layerConfig.append.locationPath; computed=$computed; expected: $digest")
        final path = "/v2/$image/blobs/$digest"
        cache.put(path, buffer, type, digest)

        final result = new HashMap()
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

        // get the layer blob
        final newLayer = layerBlob(imageName)

        // turn the json string into a json map
        // and append the new layer
        final manifest = (Map) new JsonSlurper().parseText(imageManifest)
        (manifest.layers as List).add( newLayer )

        // update the config digest
        (manifest.config as Map).digest = newImageConfigDigest

        // turn the updated manifest into a json
        final newManifest = JsonOutput.prettyPrint(JsonOutput.toJson(manifest))

        // add to the cache
        final digest = RegHelper.digest(newManifest)
        final path = "/v2/$imageName/manifests/$digest"
        cache.put(path, newManifest.bytes, ContentType.DOCKER_MANIFEST_V2_TYPE, digest)

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
        if( layerConfig.entrypoint ) {
            config.Entrypoint = layerConfig.entrypoint
        }
        if( layerConfig.cmd ) {
            config.Cmd = layerConfig.cmd
        }
        if( layerConfig.workingDir ) {
            config.WorkingDir = layerConfig.workingDir
        }
        if( layerConfig.env ) {
            config.Env = appendEnv(config.Env as List, layerConfig.env)
        }
        if( entryChain ) {
            config.Env = appendEnv( config.Env as List, [ "XREG_ENTRY_CHAIN="+entryChain ] )
        }

        config
    }

    protected String updateImageConfig(String imageName, String imageConfig) {

        final newLayer = layerConfig.append.tarDigest

        // turn the json string into a json map
        // and append the new layer
        final manifest = new JsonSlurper().parseText(imageConfig) as Map
        final rootfs = manifest.rootfs as Map
        final layers = rootfs.diff_ids as List
        layers.add( newLayer )

        // update the image config
         enrichConfig(manifest.config as Map)

        // turn the updated manifest into a json
        final newConfig = JsonOutput.toJson(manifest)

        // add to the cache
        final digest = RegHelper.digest(newConfig)
        final path = "/v2/$imageName/blobs/$digest"
        cache.put(path, newConfig.bytes, ContentType.DOCKER_IMAGE_V1, digest)

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
        log.trace "Find target digest arch: $arch ==> digest: $result"
        return result
    }

    protected void rewriteHistoryV1( List<Map> history){
        assert history.size()

        def first = history.first()

        def newHistoryId = RegHelper.random256Hex()
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

        final blob = layerBlob(imageName)
        final newLayer= [blobSum: blob.digest]
        fsLayers.add(0, newLayer)
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

        cache.put("/v2/$imageName/manifests/$newManifestDigest", newManifestJson.bytes, ContentType.DOCKER_MANIFEST_V1_JWS_TYPE, newManifestDigest)
        return newManifestDigest
    }
}
