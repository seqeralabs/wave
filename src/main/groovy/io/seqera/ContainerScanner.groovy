package io.seqera

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class ContainerScanner {

    private ProxyClient client
    private Path layerPath
    private Cache cache

    {
        layerPath = Paths.get('foo.tar.gzip')
    }

    ContainerScanner withCache(Cache cache) {
        this.cache = cache
        return this
    }

    ContainerScanner withClient(ProxyClient client) {
        this.client = client
        return this
    }

    ContainerScanner withLayer(Path path) {
        this.layerPath = path
        return this
    }


    String resolve(String imageName, String tag, Map<String,List<String>> headers) {
        assert client, "Missing client"
        // resolve image tag to digest
        final resp1 = client.head("/v2/$imageName/manifests/$tag", headers)
        final digest = resp1.headers().firstValue('docker-content-digest').get()
        log.debug "Image $imageName:$tag => digest=$digest"

        // get manifest list for digest
        final resp2 = client.getString("/v2/$imageName/manifests/$digest", headers)
        final manifestsList = resp2.body()
        log.debug "Image $imageName:$tag => manifests list=\n${JsonOutput.prettyPrint(manifestsList)}"

        // get target manifest
        final targetDigest = findTargetDigest(manifestsList, Mock.MANIFEST_MIME)
        final resp3 = client.getString("/v2/$imageName/manifests/$targetDigest", headers)
        final imageManifest = resp3.body()
        log.debug "Image $imageName:$tag => image manifest=\n${JsonOutput.prettyPrint(imageManifest)}"

        // update the image manifest adding a new layer
        // returns the updated image manifest digest
        final newDigest = updateImageManifest(imageName, imageManifest)

        // update the manifests list with the new digest
        // returns the manifests list digest
        final listDigest = updateManifestsList(imageName, manifestsList, targetDigest, newDigest)

        return listDigest
    }

    protected String updateManifestsList(String imageName, String manifestsList, String targetDigest, String newDigest) {
        final updated = manifestsList.replace(targetDigest, newDigest)
        final result = RegHelper.digest(updated)
        final type = Mock.MANIFEST_LIST_MIME
        // make sure the manifest was updated
        if( manifestsList==updated )
            throw new IllegalArgumentException("Unable to find target digest '$targetDigest' into image list manifest")
        // store in the cache
        cache.put("/v2/$imageName/manifests/$result", updated.bytes, type, result)
        // return the updated manifests list digest
        return result
    }

    protected Map layerBlob(String image) {
        assert layerPath

        // store the layer blob in the cache
        final type = "application/vnd.docker.image.rootfs.diff.tar.gzip"
        final buffer = Files.readAllBytes(layerPath)
        final digest = RegHelper.digest(buffer)
        final size = Files.size(layerPath) as int
        final path = "/v2/$image/blobs/$digest"
        cache.put(path, buffer, type, digest)

        final result = new HashMap()
        result."mediaType" = type
        result."size" = size
        result."digest" = digest
        return result
    }

    protected String updateImageManifest(String imageName, String imageManifest) {

        // get the layer blob
        final newLayer = layerBlob(imageName)

        // turn the json string into a json map
        // and append the new layer
        final manifest = (Map) new JsonSlurper().parseText(imageManifest)
        (manifest.layers as List).add( newLayer )

        // turn the updated manifest into a json
        final newManifest = JsonOutput.prettyPrint(JsonOutput.toJson(manifest))

        // add to the cache
        final digest = RegHelper.digest(newManifest)
        final path = "/v2/$imageName/manifests/$digest"
        cache.put(path, newManifest.bytes, Mock.MANIFEST_MIME, digest)

        // return the updated image manifest digest
        return digest
    }

    @CompileDynamic
    protected String findTargetDigest(String body, String mediaType, String architecture='amd64', String os='linux' ) {

        final json = (Map) new JsonSlurper().parseText(body)
        final record = json.manifests.find { record ->  println(record);  record.mediaType == mediaType && record.platform.os==os && record.platform.architecture==architecture }
        return record.digest

    }

}
