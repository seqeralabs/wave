package io.seqera

import io.seqera.controller.RegHelper
import io.seqera.model.ContentType
import io.seqera.model.LayerConfig
import io.seqera.proxy.ProxyClient

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class ContainerScanner {

    private ProxyClient client
    private Path layerConfigPath
    private String os = 'linux'
    private String arch
    private String imageName
    private String tag
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

    ContainerScanner withImage(String image){
        this.imageName = image
        this
    }

    ContainerScanner withTag(String tag){
        this.tag = tag
        this
    }

    Path getLayerConfigPath() {
        return layerConfigPath
    }

    @Memoized
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

        log.debug "Layer info: path=$layerConfig.append.location; gzip-checksum=$layerConfig.append.gzipDigest; tar-checksum=$layerConfig.append.tarDigest"
        return layerConfig
    }

    String requestDigestForTag(Map<String,List<String>> headers){
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        assert resp1.statusCode() == 200

        final digest = resp1.headers().firstValue('docker-content-digest')
        log.debug "Image $imageName:$tag => digest=$digest"
        if( digest.isEmpty() )
            throw new IllegalArgumentException("Image $imageName without digest")
        digest.get()
    }

    Map requestManifestForDigest( String digest, Map<String,List<String>> headers){
        final resp2 = client.getString("/v2/$imageName/manifests/${digest}", headers)
        assert resp2.statusCode() == 200

        final manifests =  new JsonSlurper().parseText(resp2.body()) as Map
        log.debug "Image $imageName:$tag => manifests list=\n${JsonOutput.prettyPrint(JsonOutput.toJson(manifests))}"

        manifests
    }

    Map requestManifestFromList(Map manifest, Map<String,List<String>> headers){
        def targetDigest = findTargetDigest(manifest)
        final resp3 = client.getString("/v2/$imageName/manifests/$targetDigest", headers)
        final body = resp3.body()
        log.debug "Image $imageName:$tag => image manifest=\n${JsonOutput.prettyPrint(body)}"
        new JsonSlurper().parseText(body) as Map
    }

    Map requestImageConfig(String digest, Map<String,List<String>> headers){
        final resp4 = client.getString("/v2/$imageName/blobs/$digest", headers)
        assert resp4.statusCode() == 200

        final imageConfig = resp4.body()
        log.debug "Image $imageName:$tag => image config=\n${JsonOutput.prettyPrint(imageConfig)}"
        new JsonSlurper().parseText(imageConfig) as Map
    }

    String getRandomId() {
        def length = 64 // the size of the random string
        def pool = ['a'..'f',0..9].flatten() // generating pool
        Random random = new Random(System.currentTimeMillis())
        def randomChars = (0..length-1)
                .collect { pool[random.nextInt(pool.size())] }
        randomChars.join('')
    }

    String resolveV1Manifest(Map origin, Map<String,List<String>> headers, String digest){

        def newLayer = layerBlobV1(imageName)

        def fsLayers = origin.fsLayers as List<Map>
        def history = origin.history as List<Map>

        def first = history.first()
        def firstJson= new JsonSlurper().parseText(first['v1Compatibility'].toString()) as Map

        def second = history[1]
        def secondJson= new JsonSlurper().parseText(second['v1Compatibility'].toString()) as Map

        def newHistoryJson =[
                id : randomId,
                parent: secondJson.id as String
        ]
        def newHistoryItem = [
                v1Compatibility: JsonOutput.toJson(newHistoryJson)
        ]

        firstJson.parent = newHistoryJson.id as String

        // update the image config
        final config = firstJson.config as Map
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

        // store the changes
        first['v1Compatibility'] = JsonOutput.toJson(firstJson)

        fsLayers.add(newLayer)
        history.add(1, newHistoryItem)

        def newManifestLength = JsonOutput.prettyPrint(JsonOutput.toJson(origin)).length()

        def signatures = origin.signatures as List<Map>
        def signature = signatures.first()
        def signprotected = signature.protected as String

        def protecteddecoded = new JsonSlurper().parseText(new String(signprotected.decodeBase64())) as Map
        protecteddecoded.formatLength = newManifestLength-1

        def protectedBase64 = JsonOutput.toJson(protecteddecoded).bytes.encodeBase64().toString().replaceAll('=','')
        signature.protected = protectedBase64

        def manifest = JsonOutput.prettyPrint(JsonOutput.toJson(origin))
        cache.put("/v2/$imageName/manifests/$digest", manifest.bytes, ContentType.DOCKER_MANIFEST_V1_JWS_TYPE, digest)
        digest
    }

    String resolveV2Manifest(Map manifestV2, Map<String,List<String>> headers){
        final manifestResult = findImageManifestAndDigest(manifestV2, headers)
        final imageManifest = manifestResult.first
        final configDigest = manifestResult.second
        final targetDigest = manifestResult.third

        final manifest = new JsonSlurper().parseText(imageManifest) as Map

        // fetch the image config
        final imageConfig = requestImageConfig(configDigest, headers)

        // update the image config adding the new layer
        final newConfigDigest = updateImageConfig(imageName, imageConfig)
        log.debug "==> new config digest: $newConfigDigest"
        (manifest.config as Map).digest = newConfigDigest

        // update the image manifest adding a new layer
        // returns the updated image manifest digest
        final newManifestDigest = updateImageManifest(imageName, manifest)
        log.debug "==> new image digest: $newManifestDigest"

        if( !targetDigest ) {
            return newManifestDigest
        }
        else {
            // update the manifests list with the new digest
            // returns the manifests list digest
            final newListDigest = updateManifestsList(imageName, manifestV2, targetDigest, newManifestDigest)
            log.debug "==> new list digest: $newListDigest"

            return newListDigest
        }
    }

    String resolve(Map<String,List<String>> headers) {
        assert client, "Missing client"

        // resolve image tag to digest
        final digest = requestDigestForTag(headers)

        // get manifest list for digest
        final manifest = requestManifestForDigest(digest, headers)

        if (manifest.schemaVersion == 1) {
            return resolveV1Manifest(manifest, headers, digest)
        }

        final manifestV2
        if( manifest.mediaType == ContentType.DOCKER_MANIFEST_LIST_V2){
            manifestV2 = requestManifestFromList(manifest, headers)
        }else {
            manifestV2 = manifest
        }
        return resolveV2Manifest(manifestV2, headers)
    }

    protected Tuple3<String,String,String> findImageManifestAndDigest(Map json, Map<String,List<String>> headers) {
        def targetDigest = null
        def media = json.mediaType

        if( media == ContentType.DOCKER_MANIFEST_V2_TYPE ) {
            // find the image config digest
            final configDigest = findImageConfigDigest(json)
            return new Tuple3( JsonOutput.toJson(json), configDigest, targetDigest)
        }
        else {
            throw new IllegalArgumentException("Unexpected media type for request '$imageName:$tag' - offending value: $media")
        }

    }

    protected String updateManifestsList(String imageName, Map json, String targetDigest, String newDigest) {
        final manifestsList = JsonOutput.toJson(json)
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

    protected Map layerBlob(String image) {

        // store the layer blob in the cache
        final type = "application/vnd.docker.image.rootfs.diff.tar.gzip"
        final buffer = Files.readAllBytes(layerConfig.append.locationPath)
        final digest = RegHelper.digest(buffer)
        final size = Files.size(layerConfig.append.locationPath) as int
        if( digest != layerConfig.append.gzipDigest )
            throw new IllegalArgumentException("Layer gzip computed digest does not match with expected digest -- path=$layerConfig.append.locationPath; computed=$digest; expected: $layerConfig.append.gzipDigest")
        final path = "/v2/$image/blobs/$digest"
        cache.put(path, buffer, type, digest)

        final result = new HashMap()
        result."mediaType" = type
        result."size" = size
        result."digest" = digest
        return result
    }

    protected Map layerBlobV2(String image) {
        return layerBlob(image)
    }

    protected Map layerBlobV1(String image) {
        def blob = layerBlob(image)
        [blobSum: blob.digest]
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
    protected String findImageConfigDigest(Map json) {
        return json.config.digest
    }

    protected String updateImageManifest(String imageName, Map manifest) {

        // get the layer blob
        final newLayer = layerBlobV2(imageName)

        // turn the json string into a json map
        // and append the new layer
        (manifest.layers as List).add( newLayer )

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

    protected String updateImageConfig(String imageName, Map manifest) {

        final newLayer = layerConfig.append.tarDigest

        // turn the json string into a json map
        // and append the new layer
        final rootfs = manifest.rootfs as Map
        final layers = rootfs.diff_ids as List
        layers.add( newLayer )

        // update the image config
        final config = manifest.config as Map
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
        final record = json.manifests.find( { record ->
            record.mediaType == mediaType && record.platform.os==os && record.platform.architecture==arch
        } )
        final result = record.digest
        log.trace "Find target digest arch: $arch ==> digest: $result"
        return result
    }


}
