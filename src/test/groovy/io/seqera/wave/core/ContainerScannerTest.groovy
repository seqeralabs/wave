package io.seqera.wave.core

import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.core.ContainerScanner
import io.seqera.wave.model.ContentType
import io.seqera.wave.model.LayerConfig
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.storage.Storage
import io.seqera.wave.test.ManifestConst
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerScannerTest extends Specification {

    @Inject
    Storage storage

    @Value('${wave.registries.docker.username}')
    String dockerUsername

    @Value('${wave.registries.docker.password}')
    String dockerPassword

    @Shared
    @Value('${wave.registries.quay.username}')
    String quayUsername

    @Shared
    @Value('${wave.registries.quay.password}')
    String quayPassword

    @Inject RegistryAuthService loginService
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider

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
                      "gzipSize": 1000,
                      "tarDigest": "sha256:zzz"
                    }                  
                }
                """
        and:
        def json = folder.resolve('layer.json')
        Files.write(json, CONFIG.bytes)

        when:
        def scanner = new ContainerScanner().withContainerConfig(LayerConfig.containerConfigAdapter(json))

        then:
        def config = scanner.getContainerConfig()
        and:
        config.layers[0].location == layer.toString()

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should find target digest' () {

        given:
        def body = ManifestConst.MANIFEST_LIST_CONTENT

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

        def scanner = new ContainerScanner()
                            .withStorage(storage)
                            .withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))
        and:
        def digest = RegHelper.digest(layerPath.bytes)

        when:
        def layer = scanner.getContainerConfig().layers.get(0)
        def blob = scanner.layerBlob(IMAGE, layer)

        then:
        blob.get('mediaType') == 'application/vnd.docker.image.rootfs.diff.tar.gzip'
        blob.get('digest') == digest
        blob.get('size') == layerPath.size()
        and:
        blob.get('size') == 2

        and:
        def entry = storage.getBlob("/v2/$IMAGE/blobs/$digest").get()
        entry.bytes == layerPath.bytes
        entry.mediaType == ContentType.DOCKER_IMAGE_TAR_GZIP
        entry.digest == RegHelper.digest(layerPath.bytes)

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should update image manifest' () {
        given:
        def IMAGE = 'hello-world'
        def MANIFEST = ManifestConst.MANIFEST_CONTENT
        def NEW_CONFIG_DIGEST = 'sha256:1234abcd'
        def SOURCE_JSON = new JsonSlurper().parseText(MANIFEST)

        and:
        unpackLayer()
        def layerDigest = RegHelper.digest(layerPath.bytes)

        and:

        def scanner = new ContainerScanner().withStorage(storage).withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        def digest = scanner.updateImageManifest(IMAGE, MANIFEST, NEW_CONFIG_DIGEST)

        then:
        // the cache contains the update image manifest json
        def entry = storage.getManifest("/v2/$IMAGE/manifests/$digest").get()
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
        def MANIFEST = ManifestConst.MANIFEST_LIST_CONTENT
        def DIGEST = 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'
        def NEW_DIGEST = RegHelper.digest('foo')

        and:
        unpackLayer()

        and:

        def scanner = new ContainerScanner().withStorage(storage).withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        def digest = scanner.updateManifestsList(IMAGE, MANIFEST, DIGEST, NEW_DIGEST)

        then:
        def entry = storage.getManifest("/v2/$IMAGE/manifests/$digest").get()
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

        def scanner = new ContainerScanner().withStorage(storage).withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        def digest = scanner.updateImageConfig(IMAGE_NAME, IMAGE_CONFIG)
        then:
        def entry = storage.getBlob("/v2/$IMAGE_NAME/blobs/$digest").get()
        entry.mediaType == ContentType.DOCKER_IMAGE_V1
        entry.digest == digest
        and:
        def manifest = new JsonSlurper().parseText(new String(entry.bytes))
        manifest.rootfs.diff_ids instanceof List
        manifest.rootfs.diff_ids.size() == 2

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should update image config with new layer v1' () {
        given:
        def MANIFEST = '''
{
   "schemaVersion": 1,
   "name": "biocontainers/fastqc",
   "tag": "0.11.9--0",
   "architecture": "amd64",
   "fsLayers": [
      {
         "blobSum": "sha256:6d92b3a49ebfad5fe895550c2cb24b6370d61783aa4f979702a94892cbd19077"
      },
      {
         "blobSum": "sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"
      }
   ],
   "history": [
      {
         "v1Compatibility": "{\\"architecture\\":\\"amd64\\",\\"config\\":{\\"Hostname\\":\\"\\",\\"Domainname\\":\\"\\",\\"User\\":\\"\\",\\"AttachStdin\\":false,\\"AttachStdout\\":false,\\"AttachStderr\\":false,\\"Tty\\":false,\\"OpenStdin\\":false,\\"StdinOnce\\":false,\\"Env\\":[\\"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\\"],\\"Cmd\\":[\\"/bin/sh\\"],\\"Image\\":\\"\\",\\"Volumes\\":null,\\"WorkingDir\\":\\"\\",\\"Entrypoint\\":null,\\"OnBuild\\":null,\\"Labels\\":{}},\\"container\\":\\"4be9f6b4406ec142e457fd7c43ff338511ab338b33585c30805ba2d5d3da29e3\\",\\"container_config\\":{\\"Hostname\\":\\"4be9f6b4406e\\",\\"Domainname\\":\\"\\",\\"User\\":\\"\\",\\"AttachStdin\\":false,\\"AttachStdout\\":false,\\"AttachStderr\\":false,\\"Tty\\":false,\\"OpenStdin\\":false,\\"StdinOnce\\":false,\\"Env\\":[\\"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\\"],\\"Cmd\\":[\\"/bin/sh\\"],\\"Image\\":\\"bgruening/busybox-bash:0.1\\",\\"Volumes\\":null,\\"WorkingDir\\":\\"\\",\\"Entrypoint\\":null,\\"OnBuild\\":null,\\"Labels\\":{}},\\"created\\":\\"2020-01-24T15:39:30.564518517Z\\",\\"docker_version\\":\\"17.09.0-ce\\",\\"id\\":\\"f235879f79194a5e3d4b10c3b714c36669e8e98160ba73bd9b044fdec624ceaf\\",\\"os\\":\\"linux\\",\\"parent\\":\\"b7c0567175be2a551a8ed4e60d695d33347ebae5d8cfc4a5d0381e0ce3c34222\\"}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"b7c0567175be2a551a8ed4e60d695d33347ebae5d8cfc4a5d0381e0ce3c34222\\",\\"parent\\":\\"32dbc9f4b6f9f15dfcce38773db21d7aadfc059242a821fb98bc8cf0997d05ce\\",\\"created\\":\\"2016-05-09T06:21:02.266124295Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) CMD [\\\\\\"/bin/sh\\\\\\" \\\\\\"-c\\\\\\" \\\\\\"/bin/bash\\\\\\"]\\"]},\\"author\\":\\"Bjoern Gruening \\\\u003cbjoern.gruening@gmail.com\\\\u003e\\",\\"throwaway\\":true}"
      }
   ],
   "signatures": [
      {
         "header": {
            "jwk": {
               "crv": "P-256",
               "kid": "H4I5:ZTAM:GNQK:AM3G:HF4X:RFAK:U67G:GHDB:L4S3:EBKB:Y7Z2:QPXP",
               "kty": "EC",
               "x": "ZAM8UZLnMkeRrrK2J81Vv2Whi2yHt3aevb1fKOqZenQ",
               "y": "J6Oeam_YkFuANSPxVYTFq8iZjoP7JCvIrFlABrD1JCA"
            },
            "alg": "ES256"
         },
         "signature": "4-yAR3tVSQHhFfu_LxWkuVoAW3eqeBkIK1azWYH_-4N4pKvkJCoS2CjUDzcUBNkeul38pUocgLSUUmHiSdqjUA",
         "protected": "eyJmb3JtYXRMZW5ndGgiOjY5ODksImZvcm1hdFRhaWwiOiJDbjAiLCJ0aW1lIjoiMjAyMC0wMS0yNFQxNTo0MToxNloifQ"
      }
   ]
}
       '''
        def originalManifest = new JsonSlurper().parseText(MANIFEST)
        and:
        def IMAGE_NAME = 'hello-world'

        and:
        unpackLayer()

        and:

        def scanner = new ContainerScanner().withStorage(storage).withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        def digest = scanner.resolveV1Manifest(MANIFEST, IMAGE_NAME)
        then:
        def entry = storage.getManifest("/v2/$IMAGE_NAME/manifests/$digest").get()
        entry.mediaType == ContentType.DOCKER_MANIFEST_V1_JWS_TYPE
        entry.digest == digest
        and:
        def manifest = new JsonSlurper().parseText(new String(entry.bytes))
        originalManifest.fsLayers.size() == manifest.fsLayers.size() - 1
        originalManifest.history.size() == manifest.history.size() - 1
        and:
        manifest.history.first()['v1Compatibility'].indexOf('parent') != -1
        manifest.history[1]['v1Compatibility'] == originalManifest.history[0]['v1Compatibility']
        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should add a new layer v1 format' () {
        given:
        def MANIFEST = '''
{   
   "history": [
      {
         "v1Compatibility": "{\\"architecture\\":\\"amd64\\",\\"config\\":{\\"Hostname\\":\\"\\",\\"Domainname\\":\\"\\",\\"User\\":\\"\\",\\"AttachStdin\\":false,\\"AttachStdout\\":false,\\"AttachStderr\\":false,\\"Tty\\":false,\\"OpenStdin\\":false,\\"StdinOnce\\":false,\\"Env\\":[\\"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\\"],\\"Cmd\\":[\\"/bin/sh\\"],\\"Image\\":\\"\\",\\"Volumes\\":null,\\"WorkingDir\\":\\"\\",\\"Entrypoint\\":null,\\"OnBuild\\":null,\\"Labels\\":{}},\\"container\\":\\"4be9f6b4406ec142e457fd7c43ff338511ab338b33585c30805ba2d5d3da29e3\\",\\"container_config\\":{\\"Hostname\\":\\"4be9f6b4406e\\",\\"Domainname\\":\\"\\",\\"User\\":\\"\\",\\"AttachStdin\\":false,\\"AttachStdout\\":false,\\"AttachStderr\\":false,\\"Tty\\":false,\\"OpenStdin\\":false,\\"StdinOnce\\":false,\\"Env\\":[\\"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\\"],\\"Cmd\\":[\\"/bin/sh\\"],\\"Image\\":\\"bgruening/busybox-bash:0.1\\",\\"Volumes\\":null,\\"WorkingDir\\":\\"\\",\\"Entrypoint\\":null,\\"OnBuild\\":null,\\"Labels\\":{}},\\"created\\":\\"2020-01-24T15:39:30.564518517Z\\",\\"docker_version\\":\\"17.09.0-ce\\",\\"id\\":\\"f235879f79194a5e3d4b10c3b714c36669e8e98160ba73bd9b044fdec624ceaf\\",\\"os\\":\\"linux\\",\\"parent\\":\\"b7c0567175be2a551a8ed4e60d695d33347ebae5d8cfc4a5d0381e0ce3c34222\\"}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"b7c0567175be2a551a8ed4e60d695d33347ebae5d8cfc4a5d0381e0ce3c34222\\",\\"parent\\":\\"32dbc9f4b6f9f15dfcce38773db21d7aadfc059242a821fb98bc8cf0997d05ce\\",\\"created\\":\\"2016-05-09T06:21:02.266124295Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) CMD [\\\\\\"/bin/sh\\\\\\" \\\\\\"-c\\\\\\" \\\\\\"/bin/bash\\\\\\"]\\"]},\\"author\\":\\"Bjoern Gruening \\\\u003cbjoern.gruening@gmail.com\\\\u003e\\",\\"throwaway\\":true}"
      }
   ]
}
       '''
        def originalManifest = new JsonSlurper().parseText(MANIFEST)
        def mutableManifest = new JsonSlurper().parseText(MANIFEST)

        and:
        unpackLayer()

        and:
        def scanner = new ContainerScanner().withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        scanner.rewriteHistoryV1(mutableManifest.history)

        then:
        originalManifest.history.size() == mutableManifest.history.size() - 1
        and:
        mutableManifest.history.first()['v1Compatibility'].indexOf('parent') != -1
        mutableManifest.history[1]['v1Compatibility'] == originalManifest.history[0]['v1Compatibility']

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'add a new layer v1 format requires an history' () {
        given:
        def MANIFEST = '''
{   
   "history": []
}
       '''
        def originalManifest = new JsonSlurper().parseText(MANIFEST)
        def mutableManifest = new JsonSlurper().parseText(MANIFEST)

        and:
        unpackLayer()

        and:
        def scanner = new ContainerScanner().withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        scanner.rewriteHistoryV1(mutableManifest.history)

        then:
        def e = thrown(AssertionError)

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'add a new layer v1 format requires a layer' () {
        given:
        def MANIFEST = '''
{   
   "fsLayers": []
}
       '''
        def originalManifest = new JsonSlurper().parseText(MANIFEST)
        def mutableManifest = new JsonSlurper().parseText(MANIFEST)

        and:
        unpackLayer()
        and:
        def IMAGE_NAME = 'hello-world'

        and:
        def scanner = new ContainerScanner().withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        scanner.rewriteLayersV1(IMAGE_NAME, mutableManifest.fsLayers)

        then:
        def e = thrown(AssertionError)

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'add a new layer v1 format' () {
        given:
        def MANIFEST = '''
{   
   "fsLayers": [
      {
         "blobSum": "sha256:6d92b3a49ebfad5fe895550c2cb24b6370d61783aa4f979702a94892cbd19077"
      },
      {
         "blobSum": "sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"
      }
   ]
}
       '''
        def originalManifest = new JsonSlurper().parseText(MANIFEST)
        def mutableManifest = new JsonSlurper().parseText(MANIFEST)

        and:
        unpackLayer()
        and:
        def IMAGE_NAME = 'hello-world'

        and:

        def scanner = new ContainerScanner().withStorage(storage).withContainerConfig(LayerConfig.containerConfigAdapter(Paths.get(layerJson.absolutePath)))

        when:
        scanner.rewriteLayersV1(IMAGE_NAME, mutableManifest.fsLayers)

        then:
        originalManifest.fsLayers.size() == mutableManifest.fsLayers.size() - 1
        originalManifest.fsLayers.first().blobSum != mutableManifest.fsLayers.first().blobSum
        originalManifest.fsLayers.first().blobSum == mutableManifest.fsLayers[1].blobSum

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should resolve busybox' () {
        given:
        def REG = 'docker.io'
        def IMAGE = 'library/busybox'
        def registry = lookupService.lookup(REG)
        def creds = credentialsProvider.getCredentials(REG)
        and:

        def client = new ProxyClient()
                .withRoute(Mock(RoutePath))
                .withImage(IMAGE)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
        and:
        def scanner = new ContainerScanner()
                .withStorage(storage)
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
        and:
        storage.getManifest("/v2/$IMAGE/manifests/$digest")
    }

}
