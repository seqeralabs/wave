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
import io.seqera.wave.WaveDefault
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.auth.RegistryAuth
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.model.ContentType
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.storage.Storage
import io.seqera.wave.test.ManifestConst
import io.seqera.wave.util.ContainerConfigFactory
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerAugmenterTest extends Specification {

    @Inject
    Storage storage

    @Value('${wave.registries.docker.io.username}')
    String dockerUsername

    @Value('${wave.registries.docker.io.password}')
    String dockerPassword

    @Shared
    @Value('${wave.registries.quay.io.username}')
    String quayUsername

    @Shared
    @Value('${wave.registries.quay.io.password}')
    String quayPassword

    @Inject RegistryAuthService loginService
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject HttpClientConfig httpClientConfig

    def 'should set layer paths' () {
        given:
        def folder = Files.createTempDirectory('test')
        def layer = folder.resolve('dummy.gzip');
        Files.createFile(layer)
        and:
        def CONFIG = """
                {
                    "layers": [{
                      "location": "${layer.toAbsolutePath()}",
                      "gzipDigest": "sha256:xxx",
                      "gzipSize": 1000,
                      "tarDigest": "sha256:zzz"
                    }]                  
                }
                """
        and:
        def json = folder.resolve('layer.json')
        Files.write(json, CONFIG.bytes)

        when:
        def scanner = new ContainerAugmenter().withContainerConfig(ContainerConfigFactory.instance.from(json))

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
        def result = new ContainerAugmenter()
                .withPlatform('amd64')
                .findTargetDigest(body, false)
        then:
        result == 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'

        when:
        result = new ContainerAugmenter()
                .withPlatform('arm64')
                .findTargetDigest(body, false)
        then:
        result == 'sha256:01433e86a06b752f228e3c17394169a5e21a0995f153268a9b36a16d4f2b2184'

        when:
        result = new ContainerAugmenter()
                .withPlatform('linux/arm/v7')
                .findTargetDigest(body, false)
        then:
        result == 'sha256:f130bd2d67e6e9280ac6d0a6c83857bfaf70234e8ef4236876eccfbd30973b1c'
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
        layerConfig.layers[0].location = layerPath.absolutePath

        layerJson = new File("$folder/layer.json")
        layerJson.text = JsonOutput.prettyPrint(JsonOutput.toJson(layerConfig))
    }

    def 'create the layer' () {
        given:
        def IMAGE = 'hello-world'
        def REGISTRY = 'docker.io'
        and:
        unpackLayer()

        def info = new RegistryInfo(REGISTRY, new URI('http://docker.io'), Mock(RegistryAuth))
        def client = Mock(ProxyClient) { getRegistry()>>info }
        def config = ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath))
        def scanner = new ContainerAugmenter()
                            .withStorage(storage)
                            .withContainerConfig(config)
                            .withClient(client)
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
        def entry = storage.getBlob("$REGISTRY/v2/$IMAGE/blobs/$digest").get()
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
        def NEW_CONFIG_SIZE = 100
        def SOURCE_JSON = new JsonSlurper().parseText(MANIFEST)
        def REGISTRY = 'docker.io'

        and:
        unpackLayer()
        def layerDigest = RegHelper.digest(layerPath.bytes)

        and:
        def info = new RegistryInfo(REGISTRY, new URI('http://docker.io'), Mock(RegistryAuth))
        def client = Mock(ProxyClient) { getRegistry()>>info }
        def scanner = new ContainerAugmenter()
                .withStorage(storage)
                .withClient(client)
                .withContainerConfig(ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath)))

        when:
        def result = scanner.updateImageManifest(IMAGE, MANIFEST, NEW_CONFIG_DIGEST, NEW_CONFIG_SIZE, false)

        then:
        // the cache contains the update image manifest json
        def entry = storage.getManifest("$REGISTRY/v2/$IMAGE/manifests/$result.v1").get()
        def manifest = new String(entry.bytes)
        def json = new JsonSlurper().parseText(manifest)
        and:
        entry.mediaType == ContentType.DOCKER_MANIFEST_V2_TYPE
        entry.digest == result.v1
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
        json.config.size == NEW_CONFIG_SIZE

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should update manifests list' () {
        given:
        def MANIFEST = '''
            {
               "manifests":[
                  {
                     "digest":"sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4",
                     "mediaType":"application\\/vnd.docker.distribution.manifest.v2+json",
                     "platform":{
                        "architecture":"amd64",
                        "os":"linux"
                     },
                     "size":525
                  },
                  {
                     "digest":"sha256:01433e86a06b752f228e3c17394169a5e21a0995f153268a9b36a16d4f2b2184",
                     "mediaType":"application\\/vnd.docker.distribution.manifest.v2+json",
                     "platform":{
                        "architecture":"arm64",
                        "os":"linux"
                     },
                     "size":626
                  }
               ],
               "mediaType":"application\\/vnd.docker.distribution.manifest.list.v2+json",
               "schemaVersion":2
            }
            '''.stripIndent(true)
        and:
        def IMAGE = 'hello-world'
        def DIGEST = 'sha256:f54a58bc1aac5ea1a25d796ae155dc228b3f0e11d046ae276b39c4bf2f13d8c4'
        def NEW_DIGEST = RegHelper.digest('foo')
        def NEW_SIZE = 123
        def REGISTRY = 'docker.io'
        and:
        unpackLayer()

        and:
        def info = new RegistryInfo(REGISTRY, new URI('http://docker.io'), Mock(RegistryAuth))
        def client = Mock(ProxyClient) { getRegistry()>>info }
        def scanner = new ContainerAugmenter()
                .withStorage(storage)
                .withClient(client)
                .withContainerConfig(ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath)))

        when:
        def digest = scanner.updateImageIndex(IMAGE, MANIFEST, DIGEST, NEW_DIGEST, NEW_SIZE, false)

        then:
        def entry = storage.getManifest("$REGISTRY/v2/$IMAGE/manifests/$digest").get()
        def manifest = new String(entry.bytes)
        and:
        entry.mediaType == ContentType.DOCKER_IMAGE_INDEX_V2
        entry.digest == digest
        and:
        with(new JsonSlurper().parseText(manifest) as Map) {
            manifests[0].digest == NEW_DIGEST
            manifests[0].size == NEW_SIZE
            manifests[0].mediaType == 'application/vnd.docker.distribution.manifest.v2+json'
            manifests[0].platform == [architecture: 'amd64', os: 'linux']
            and:
            manifests[1].digest == 'sha256:01433e86a06b752f228e3c17394169a5e21a0995f153268a9b36a16d4f2b2184'
            manifests[1].size == 626
            manifests[1].mediaType == 'application/vnd.docker.distribution.manifest.v2+json'
            manifests[1].platform == [architecture: 'arm64', os: 'linux']
        }

        cleanup:
        folder?.toFile()?.deleteDir()
    }

    def 'should find image config digest' () {
        given:
        def scanner = new ContainerAugmenter()
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
        def REGISTRY = 'docker.io'
        and:
        unpackLayer()

        and:
        def info = new RegistryInfo(REGISTRY, new URI('http://docker.io'), Mock(RegistryAuth))
        def client = Mock(ProxyClient) { getRegistry()>>info }
        def scanner = new ContainerAugmenter()
                .withStorage(storage)
                .withClient(client)
                .withContainerConfig(ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath)))

        when:
        def (digest, config) = scanner.updateImageConfig(IMAGE_NAME, IMAGE_CONFIG, false)
        then:
        def entry = storage.getBlob("$REGISTRY/v2/$IMAGE_NAME/blobs/$digest").get()
        entry.mediaType == ContentType.DOCKER_IMAGE_CONFIG_V1
        entry.digest == digest
        and:
        def manifest = new JsonSlurper().parseText(new String(entry.bytes))
        manifest.rootfs.diff_ids instanceof List
        manifest.rootfs.diff_ids.size() == 2
        and:
        config == new String(entry.bytes)

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
        def REGISTRY = 'docker.io'
        and:
        unpackLayer()

        and:
        def info = new RegistryInfo(REGISTRY, new URI('http://docker.io'), Mock(RegistryAuth))
        def client = Mock(ProxyClient) { getRegistry()>>info }
        def scanner = new ContainerAugmenter()
                .withStorage(storage)
                .withClient(client)
                .withContainerConfig(ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath)))

        when:
        def digest = scanner.resolveV1Manifest(MANIFEST, IMAGE_NAME)
        then:
        def entry = storage.getManifest("$REGISTRY/v2/$IMAGE_NAME/manifests/$digest").get()
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

    def 'should not rewrite the history' () {
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

        and:
        def manifest = new JsonSlurper().parseText(MANIFEST) as Map

        and:
        def scanner = new ContainerAugmenter()
        def history = new ArrayList(manifest.history)

        when:
        scanner.rewriteHistoryV1(history)
        then:
        history == manifest.history
    }

    def 'should update top history entry config' () {
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

        and:
        def manifest = new JsonSlurper().parseText(MANIFEST) as Map

        and:
        def config = new ContainerConfig(workingDir: '/some/work/dir',entrypoint: ['my','entry'], env: ['THIS=THAT'])
        def scanner = new ContainerAugmenter().withContainerConfig(config)
        def history = new ArrayList(manifest.history)

        when:
        scanner.rewriteHistoryV1(history)
        then:
        history.size() == 2
        history.size() == manifest.history.size()
        and:
        history[1] == manifest.history[1]
        and:
        history[0] != manifest.history[0]

        and:
        def v1Compatibility = new JsonSlurper().parseText(history[0].v1Compatibility) as Map
        v1Compatibility.architecture == 'amd64'
        v1Compatibility.id == 'f235879f79194a5e3d4b10c3b714c36669e8e98160ba73bd9b044fdec624ceaf'
        v1Compatibility.parent == 'b7c0567175be2a551a8ed4e60d695d33347ebae5d8cfc4a5d0381e0ce3c34222'

        and:
        def v1Config = v1Compatibility.config as Map
        and:
        v1Config.WorkingDir == '/some/work/dir'
        v1Config.Entrypoint == ['my','entry']
        v1Config.Env == ['PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin', 'THIS=THAT']

    }

    Map v1Compatibility(value) {
        new JsonSlurper().parseText(value.v1Compatibility)
    }

    def 'should add new layers to v1 history' () {
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
        and:
        def manifest = new JsonSlurper().parseText(MANIFEST) as Map

        and:
        def layers = new ArrayList<ContainerLayer>()
        layers << new ContainerLayer(location: '/path1', tarDigest: 'sha256:123456', gzipDigest: 'sha256:123456', gzipSize: 1 )
        layers << new ContainerLayer(location: '/path2', tarDigest: 'sha256:567890', gzipDigest: 'sha256:567890', gzipSize: 2 )
        and:
        def config = new ContainerConfig(workingDir: '/some/work/dir',entrypoint: ['my','entry'], env: ['THIS=THAT'], layers: layers)
        def scanner = new ContainerAugmenter().withContainerConfig(config)
        def history = new ArrayList(manifest.history)

        when:
        scanner.rewriteHistoryV1(history)
        then:
        history.size() == 4
        manifest.history.size() == 2
        and:
        history[2] == manifest.history[0]        
        history[3] == manifest.history[1]

        and:
        v1Compatibility(history[0]).id == RegHelper.stringToId(layers[-1].tarDigest)
        v1Compatibility(history[1]).id == v1Compatibility(history[0]).parent
        v1Compatibility(history[2]).id == v1Compatibility(history[1]).parent
        v1Compatibility(history[3]).id == v1Compatibility(history[2]).parent
        v1Compatibility(history[3]).parent == '32dbc9f4b6f9f15dfcce38773db21d7aadfc059242a821fb98bc8cf0997d05ce'

        and:
        def v1Compatibility = new JsonSlurper().parseText(history[0].v1Compatibility) as Map
        v1Compatibility.architecture == 'amd64'

        and:
        def v1Config = v1Compatibility.config as Map
        and:
        v1Config.WorkingDir == '/some/work/dir'
        v1Config.Entrypoint == ['my','entry']
        v1Config.Env == ['PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin', 'THIS=THAT']
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
        def scanner = new ContainerAugmenter().withContainerConfig(ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath)))

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
        def scanner = new ContainerAugmenter().withContainerConfig(ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath)))

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
        def REGISTRY = 'docker.io'
        and:

        def info = new RegistryInfo(REGISTRY, new URI('http://docker.io'), Mock(RegistryAuth))
        def client = Mock(ProxyClient) { getRegistry()>>info }
        def scanner = new ContainerAugmenter()
                .withStorage(storage)
                .withClient(client)
                .withContainerConfig(ContainerConfigFactory.instance.from(Paths.get(layerJson.absolutePath)))

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
        def REGISTRY = 'docker.io'
        def IMAGE = 'library/busybox'
        def TAG = 'latest'
        def registry = lookupService.lookup(REGISTRY)
        def creds = credentialsProvider.getDefaultCredentials(REGISTRY)
        and:

        def client = new ProxyClient(httpClientConfig)
                .withRoute(Mock(RoutePath))
                .withImage(IMAGE)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
        and:
        def scanner = new ContainerAugmenter()
                .withStorage(storage)
                .withClient(client)
                .withPlatform('amd64')
        when:
        def digest = scanner.resolve(IMAGE, TAG, WaveDefault.ACCEPT_HEADERS)
        then:
        digest
        and:
        storage.getManifest("$REGISTRY/v2/$IMAGE/manifests/${digest.target}")
    }

    def 'should fetch container manifest' () {
        given:
        def REGISTRY = 'docker.io'
        def IMAGE = 'library/busybox'
        def registry = lookupService.lookup(REGISTRY)
        def creds = credentialsProvider.getDefaultCredentials(REGISTRY)
        and:

        def client = new ProxyClient(httpClientConfig)
                .withRoute(Mock(RoutePath))
                .withImage(IMAGE)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
        and:
        def scanner = new ContainerAugmenter()
                .withClient(client)
                .withPlatform('amd64')

        when:
        def manifest = scanner.getImageConfig(IMAGE, 'latest', WaveDefault.ACCEPT_HEADERS)
        then:
        manifest.architecture == 'amd64'
        manifest.config.cmd == ['sh']
    }

    def 'should fetch container manifest for legacy image' () {
        given:
        def REGISTRY = 'quay.io'
        def IMAGE = 'biocontainers/fastqc'
        def TAG = '0.11.9--0'
        def registry = lookupService.lookup(REGISTRY)
        def creds = credentialsProvider.getDefaultCredentials(REGISTRY)
        and:

        def client = new ProxyClient(httpClientConfig)
                .withRoute(Mock(RoutePath))
                .withImage(IMAGE)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
        and:
        def scanner = new ContainerAugmenter()
                .withClient(client)
                .withPlatform('amd64')

        when:
        def manifest = scanner.getImageConfig(IMAGE, TAG, WaveDefault.ACCEPT_HEADERS)
        then:
        manifest.architecture == 'amd64'
        manifest.config.cmd == ['/bin/sh']
    }

}
