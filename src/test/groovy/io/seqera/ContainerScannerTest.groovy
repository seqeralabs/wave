package io.seqera

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.seqera.auth.SimpleAuthProvider
import io.seqera.controller.RegHelper
import io.seqera.model.ContentType
import io.seqera.proxy.ProxyClient
import spock.lang.Ignore
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerScannerTest extends Specification {

    def createConfig(Path folder, Map config, byte[] content ){
        def location = folder.resolve('dummy.gzip')
    }

    def 'should set layer paths' () {
        given:
        def folder = Files.createTempDirectory('test')
        def layer = folder.resolve('dummy.gzip');
        Files.createFile(layer)
        and:
        def CONFIG = """
                {
                    "append": {
                      "location": "${layer.toAbsolutePath()}",
                      "gzipDigest": "sha256:xxx",
                      "tarDigest": "sha256:zzz"
                    }                  
                }
                """
        and:
        def json = folder.resolve('layer.json')
        Files.write(json, CONFIG.bytes)

        when:
        def scanner = new ContainerScanner().withLayerConfig(json)

        then:
        def config = scanner.getLayerConfig()
        and:
        config.append.locationPath == layer

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should find target digest' () {

        given:
        def body = Mock.MANIFEST_LIST_CONTENT

        when:
        def scanner = new ContainerScanner().withArch('x86_64')
        def result = scanner.findTargetDigest(body)
        then:
        result == 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'

        when:
        scanner = new ContainerScanner().withArch('amd64')
        result = scanner.findTargetDigest(body)
        then:
        result == 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'

        when:
        scanner = new ContainerScanner().withArch('arm64')
        result = scanner.findTargetDigest(body)
        then:
        result == 'sha256:01433e86a06b752f228e3c17394169a5e21a0995f153268a9b36a16d4f2b2184'
    }

    Path folder
    File layerPath
    File layerJson

    def unpackLayer(){
        folder = Files.createTempDirectory('test')
        layerPath =  Files.createFile(Paths.get(folder.toString(),"dummy.gzip")).toFile()
        layerPath.bytes = this.class.getResourceAsStream("/foo/dummy.gzip").bytes

        def string = this.class.getResourceAsStream("/foo/layer.json").text
        def layerConfig = new JsonSlurper().parseText(string)
        layerConfig.append.location = layerPath.absolutePath

        layerJson = new File("$folder/layer.json")
        layerJson.text = JsonOutput.prettyPrint(JsonOutput.toJson(layerConfig))
    }

    def 'create the layer' () {
        given:
        def IMAGE = 'hello-world'

        and:
        unpackLayer()

        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayerConfig(Paths.get(layerJson.absolutePath))
        and:
        def digest = RegHelper.digest(layerPath.bytes)
        
        when:
        def blob = scanner.layerBlob(IMAGE)

        then:
        blob.get('mediaType') == 'application/vnd.docker.image.rootfs.diff.tar.gzip'
        blob.get('digest') == digest
        blob.get('size') == layerPath.size()
        and:
        blob.get('size') == 2

        and:
        def entry = cache.get("/v2/$IMAGE/blobs/$digest")
        entry.bytes == layerPath.bytes
        entry.mediaType == ContentType.DOCKER_IMAGE_TAR_GZIP
        entry.digest == RegHelper.digest(layerPath.bytes)

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should update image manifest' () {
        given:
        def IMAGE = 'hello-world'
        def MANIFEST = Mock.MANIFEST_CONTENT
        def NEW_CONFIG_DIGEST = 'sha256:1234abcd'
        def SOURCE_JSON = new JsonSlurper().parseText(MANIFEST)

        and:
        unpackLayer()
        def layerDigest = RegHelper.digest(layerPath.bytes)

        and:
        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayerConfig(Paths.get(layerJson.absolutePath))

        when:
        def digest = scanner.updateImageManifest(IMAGE, MANIFEST, NEW_CONFIG_DIGEST)

        then:
        // the cache contains the update image manifest json
        def entry = cache.get("/v2/$IMAGE/manifests/$digest")
        def manifest = new String(entry.bytes)
        def json = new JsonSlurper().parseText(manifest)
        and:
        entry.mediaType == ContentType.DOCKER_MANIFEST_V2_TYPE
        entry.digest == digest
        and:
        // a new layer is added to the manifest
        json.layers.size() == 2
        and:
        // the original layer size is not changed
        json.layers[0].get('digest') == SOURCE_JSON.layers[0].digest
        json.layers[0].get('mediaType') == ContentType.DOCKER_IMAGE_TAR_GZIP
        and:
        // the new layer is valid
        json.layers[1].get('digest') == layerDigest
        json.layers[1].get('size') == layerPath.size()
        json.layers[1].get('mediaType') == ContentType.DOCKER_IMAGE_TAR_GZIP
        and:
        json.config.digest == NEW_CONFIG_DIGEST

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should update manifests list' () {
        given:
        def IMAGE = 'hello-world'
        def MANIFEST = Mock.MANIFEST_LIST_CONTENT
        def DIGEST = 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'
        def NEW_DIGEST = RegHelper.digest('foo')

        and:
        unpackLayer()

        and:
        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayerConfig(Paths.get(layerJson.absolutePath))

        when:
        def digest = scanner.updateManifestsList(IMAGE, MANIFEST, DIGEST, NEW_DIGEST)

        then:
        def entry = cache.get("/v2/$IMAGE/manifests/$digest")
        def manifest = new String(entry.bytes)
        and:
        entry.mediaType == ContentType.DOCKER_MANIFEST_LIST_V2
        entry.digest == digest
        and:
        manifest == MANIFEST.replace(DIGEST, NEW_DIGEST)

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should find image config digest' () {
        given:
        def scanner = new ContainerScanner()
        def CONFIG = '''
          {
            "schemaVersion": 2,
            "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
            "config": {
                "mediaType": "application/vnd.docker.container.image.v1+json",
                "size": 1469,
                "digest": "sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412"
            },
            "layers": [
                {
                  "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                  "size": 2479,
                  "digest": "sha256:2db29710123e3e53a794f2694094b9b4338aa9ee5c40b930cb8063a1be392c54"
                }
            ]
          }
        '''
        when:
        def result = scanner.findImageConfigDigest(CONFIG)
        then:
        result == 'sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412'
    }

    def 'should update image config with new layer' () {
        given:
        def IMAGE_CONFIG = '''
        {
          "architecture":"amd64",
          "config":{
              "Hostname":"",
              "Domainname":"",
              "User":"",
              "AttachStdin":false,
              "AttachStdout":false,
              "AttachStderr":false,
              "Tty":false,
              "OpenStdin":false,
              "StdinOnce":false,
              "Env":[
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
              ],
              "Cmd":[
                "/hello"
              ],
              "Image":"sha256:b9935d4e8431fb1a7f0989304ec86b3329a99a25f5efdc7f09f3f8c41434ca6d",
              "Volumes":null,
              "WorkingDir":"",
              "Entrypoint":null,
              "OnBuild":null,
              "Labels":null
          },
          "container":"8746661ca3c2f215da94e6d3f7dfdcafaff5ec0b21c9aff6af3dc379a82fbc72",
          "container_config":{
              "Hostname":"8746661ca3c2",
              "Domainname":"",
              "User":"",
              "AttachStdin":false,
              "AttachStdout":false,
              "AttachStderr":false,
              "Tty":false,
              "OpenStdin":false,
              "StdinOnce":false,
              "Env":[
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
              ],
              "Cmd":[
                "/bin/sh",
                "-c",
                "#(nop) ",
                "CMD [\\"/hello\\"]"
              ],
              "Image":"sha256:b9935d4e8431fb1a7f0989304ec86b3329a99a25f5efdc7f09f3f8c41434ca6d",
              "Volumes":null,
              "WorkingDir":"",
              "Entrypoint":null,
              "OnBuild":null,
              "Labels":{
                
              }
          },
          "created":"2021-09-23T23:47:57.442225064Z",
          "docker_version":"20.10.7",
          "history":[
              {
                "created":"2021-09-23T23:47:57.098990892Z",
                "created_by":"/bin/sh -c #(nop) COPY file:50563a97010fd7ce1ceebd1fa4f4891ac3decdf428333fb2683696f4358af6c2 in / "
              },
              {
                "created":"2021-09-23T23:47:57.442225064Z",
                "created_by":"/bin/sh -c #(nop)  CMD [\\"/hello\\"]",
                "empty_layer":true
              }
          ],
          "os":"linux",
          "rootfs":{
              "type":"layers",
              "diff_ids":[
                "sha256:e07ee1baac5fae6a26f30cabfe54a36d3402f96afda318fe0a96cec4ca393359"
              ]
          }
        }
       '''
        and:
        def IMAGE_NAME = 'hello-world'

        and:
        unpackLayer()

        and:
        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayerConfig(Paths.get(layerJson.absolutePath))

        when:
        def digest = scanner.updateImageConfig(IMAGE_NAME, IMAGE_CONFIG)
        then:
        def entry = cache.get("/v2/$IMAGE_NAME/blobs/$digest")
        entry.mediaType == ContentType.DOCKER_IMAGE_V1
        entry.digest == digest
        and:
        def manifest = new JsonSlurper().parseText(new String(entry.bytes))
        manifest.rootfs.diff_ids instanceof List
        manifest.rootfs.diff_ids.size() == 2

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    @Ignore
    def 'should resolve busybox' () {
        given:
        def HOST = 'registry-1.docker.io'
        def IMAGE = 'library/busybox'
        and:
        def layerPath = Paths.get('foo.tar.gzip')
        def cache = new Cache()
        def client = new ProxyClient(HOST, IMAGE, new SimpleAuthProvider(
                username: Mock.DOCKER_USER,
                password: Mock.DOCKER_PAT,
                authUrl: 'auth.docker.io/token',
                service: 'registry.docker.io'))
        and:
        def scanner = new ContainerScanner()
                            .withCache(cache)
                            .withClient(client)
                            .withArch('amd64')
        and:
        def headers = [
                Accept: ['application/vnd.docker.distribution.manifest.v1+prettyjws',
                        'application/json',
                        'application/vnd.oci.image.manifest.v1+json',
                        'application/vnd.docker.distribution.manifest.v2+json',
                        'application/vnd.docker.distribution.manifest.list.v2+json',
                        'application/vnd.oci.image.index.v1+json' ] ]
        when:
        def digest = scanner.resolve(IMAGE, 'latest', headers)
        then:
        digest
    }
}
