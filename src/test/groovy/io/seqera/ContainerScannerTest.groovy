package io.seqera

import java.nio.file.Files
import java.nio.file.Paths

import groovy.json.JsonSlurper
import spock.lang.Ignore
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerScannerTest extends Specification {

    private String username = "pditommaso"
    private String password = 'd213e955-3357-4612-8c48-fa5652ad968b'

    def 'should find target digest' () {

        given:
        def body = Mock.MANIFEST_LIST_CONTENT
        def scanner = new ContainerScanner()

        when:
        def result = scanner.findTargetDigest(body, Mock.MANIFEST_MIME)
        then:
        result == 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'
    }

    def 'create the layer' () {
        given:
        def IMAGE = 'hello-world'
        def layerPath = Paths.get('foo.tar.gzip')
        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayer(layerPath)
        and:
        def digest = RegHelper.digest(Files.readAllBytes(layerPath) )
        
        when:
        def blob = scanner.layerBlob(IMAGE)

        then:
        blob.get('mediaType') == 'application/vnd.docker.image.rootfs.diff.tar.gzip'
        blob.get('digest') == digest
        blob.get('size') == Files.size(layerPath)
        and:
        blob.get('size') == 10240

        and:
        def entry = cache.get("/v2/$IMAGE/blobs/$digest")
        entry.bytes == Files.readAllBytes(layerPath)
        entry.mediaType == Mock.BLOB_MIME
        entry.digest == RegHelper.digest(Files.readAllBytes(layerPath))
    }

    def 'should update image manifest' () {
        given:
        def IMAGE = 'hello-world'
        def MANIFEST = Mock.MANIFEST_CONTENT
        def SOURCE_JSON = new JsonSlurper().parseText(MANIFEST)
        def layerPath = Paths.get('foo.tar.gzip')
        def layerDigest = RegHelper.digest(Files.readAllBytes(layerPath))
        and:
        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayer(layerPath)

        when:
        def digest = scanner.updateImageManifest(IMAGE, MANIFEST)

        then:
        // the cache contains the update image manifest json
        def entry = cache.get("/v2/$IMAGE/manifests/$digest")
        def manifest = new String(entry.bytes)
        def json = new JsonSlurper().parseText(manifest)
        and:
        entry.mediaType == Mock.MANIFEST_MIME
        entry.digest == digest
        and:
        // a new layer is added to the manifest
        json.layers.size() == 2
        and:
        // the original layer size is not changed
        json.layers[0].get('digest') == SOURCE_JSON.layers[0].digest
        json.layers[0].get('mediaType') == Mock.BLOB_MIME
        and:
        // the new layer is valid
        json.layers[1].get('digest') == layerDigest
        json.layers[1].get('size') == Files.size(layerPath)
        json.layers[1].get('mediaType') == Mock.BLOB_MIME
    }

    def 'should update manifests list' () {
        given:
        def IMAGE = 'hello-world'
        def MANIFEST = Mock.MANIFEST_LIST_CONTENT
        def DIGEST = 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'
        def NEW_DIGEST = RegHelper.digest('foo')
        def layerPath = Paths.get('foo.tar.gzip')
        and:
        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayer(layerPath)

        when:
        def digest = scanner.updateManifestsList(IMAGE, MANIFEST, DIGEST, NEW_DIGEST)

        then:
        def entry = cache.get("/v2/$IMAGE/manifests/$digest")
        def manifest = new String(entry.bytes)
        and:
        entry.mediaType == Mock.MANIFEST_LIST_MIME
        entry.digest == digest
        and:
        manifest == MANIFEST.replace(DIGEST, NEW_DIGEST)
    }

    @Ignore
    def 'should resolve busybox' () {
        given:
        def layerPath = Paths.get('foo.tar.gzip')
        def cache = new Cache()
        def client = new ProxyClient(username, password, 'library/busybox')
        def scanner = new ContainerScanner().withCache(cache).withLayer(layerPath).withClient(client)
        and:
        def headers = [
                Accept: ['application/vnd.docker.distribution.manifest.v1+prettyjws',
                        'application/json',
                        'application/vnd.oci.image.manifest.v1+json',
                        'application/vnd.docker.distribution.manifest.v2+json',
                        'application/vnd.docker.distribution.manifest.list.v2+json',
                        'application/vnd.oci.image.index.v1+json' ] ]
        when:
        def digest = scanner.resolve('library/busybox', 'latest', headers)
        then:
        digest
    }
}
