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
                      "gzipSize": 1000,
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
      },
      {
         "blobSum": "sha256:10c3bb32200bdb5006b484c59b5f0c71b4dbab611d33fca816cd44f9f5ce9e3c"
      },
      {
         "blobSum": "sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"
      },
      {
         "blobSum": "sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"
      },
      {
         "blobSum": "sha256:dfda3e01f2b637b7b89adb401f2f763d592fcedd2937240e2eb3286fabce55f0"
      },
      {
         "blobSum": "sha256:d2ba336f2e4458a9223203bf17cc88d77e3006d9cbf4f0b24a1618d0a5b82053"
      },
      {
         "blobSum": "sha256:7ff999a2256f84141f17d07d26539acea8a4d9c149fefbbcc9a8b4d15ea32de7"
      },
      {
         "blobSum": "sha256:00cf8b9f3d2a08745635830064530c931d16f549d031013a9b7c6535e7107b88"
      },
      {
         "blobSum": "sha256:3aaade50789a6510c60e536f5e75fe8b8fc84801620e575cb0435e2654ffd7f6"
      },
      {
         "blobSum": "sha256:77c6c00e8b61bb628567c060b85690b0b0561bb37d8ad3f3792877bddcfe2500"
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
      },
      {
         "v1Compatibility": "{\\"id\\":\\"32dbc9f4b6f9f15dfcce38773db21d7aadfc059242a821fb98bc8cf0997d05ce\\",\\"parent\\":\\"ce56e62b24426c8b57a4e3f23fcf0cab885f0d4c7669f2b3da6d17b4b9ac7268\\",\\"created\\":\\"2016-05-09T06:21:02.050926818Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) ADD file:8583f81843640f66efa0cce8dc4f49fd769ed485caa0678ef455aa65876b03e2 in /bin/bash\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"ce56e62b24426c8b57a4e3f23fcf0cab885f0d4c7669f2b3da6d17b4b9ac7268\\",\\"parent\\":\\"03cffc43c0a5cf4268bbf830cf917e5d089f74e03609d1b3c79522e6043d769e\\",\\"created\\":\\"2016-05-08T21:00:29.07168933Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) MAINTAINER Bjoern Gruening \\\\u003cbjoern.gruening@gmail.com\\\\u003e\\"]},\\"throwaway\\":true}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"03cffc43c0a5cf4268bbf830cf917e5d089f74e03609d1b3c79522e6043d769e\\",\\"parent\\":\\"0cc77c3b7a87611a9e75e6a2fccd25ed3c2ca4802a4b31717b0af64080c59929\\",\\"created\\":\\"2015-08-11T21:02:14.747794245Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) CMD [\\\\\\"/bin/sh\\\\\\"]\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"0cc77c3b7a87611a9e75e6a2fccd25ed3c2ca4802a4b31717b0af64080c59929\\",\\"parent\\":\\"0f7d738a9dc69d3820b5f7410d42053d293b02d08e8e11a65d82aa1894bd704e\\",\\"created\\":\\"2015-08-11T21:02:14.521653891Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c opkg-cl install http://downloads.openwrt.org/snapshots/trunk/x86/64/packages/base/libc_1.1.10-1_x86_64.ipk\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"0f7d738a9dc69d3820b5f7410d42053d293b02d08e8e11a65d82aa1894bd704e\\",\\"parent\\":\\"29e08e6a06e056bcd283f52a93b4c81b6382c0607bcdf5056c0c6b97e98b93a4\\",\\"created\\":\\"2015-08-11T21:02:13.058801425Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c opkg-cl install http://downloads.openwrt.org/snapshots/trunk/x86/64/packages/base/libgcc_4.8-linaro-1_x86_64.ipk\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"29e08e6a06e056bcd283f52a93b4c81b6382c0607bcdf5056c0c6b97e98b93a4\\",\\"parent\\":\\"5174b5bc351e5d990a556e34932388f9f034e057631cfd0cacc47347c3607d57\\",\\"created\\":\\"2015-08-11T21:02:12.018991461Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) ADD file:d01ddbb13c1e847e7064ac35cbdc5c840c3a64c9aef2552aec5315a5375da312 in /lib/functions.sh\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"5174b5bc351e5d990a556e34932388f9f034e057631cfd0cacc47347c3607d57\\",\\"parent\\":\\"9b94b4d44827aaf8b87b0f5e5f65ef391f15dbd19575ff13296c3ad856e13ace\\",\\"created\\":\\"2015-08-11T21:02:11.542704863Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) ADD file:e2c3819e14cb4b8d2fff4ba6b2e2f49992788470d18d18f4aba3f3aaf2b30d40 in /usr/bin/opkg-install\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"9b94b4d44827aaf8b87b0f5e5f65ef391f15dbd19575ff13296c3ad856e13ace\\",\\"parent\\":\\"84249641cd2c5dd2da40ca5c7e20cc1429d821edc6172559380c6ca2c30bb356\\",\\"created\\":\\"2015-08-11T21:02:11.097787026Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) ADD file:1fb1c8c23666e2dc3a1cfe388868f16a3b46cbfa5b2dfd5b43382adfd1599527 in /etc/opkg.conf\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"84249641cd2c5dd2da40ca5c7e20cc1429d821edc6172559380c6ca2c30bb356\\",\\"parent\\":\\"3690474eb5b4b26fdfbd89c6e159e8cc376ca76ef48032a30fa6aafd56337880\\",\\"created\\":\\"2015-08-11T21:02:10.601706052Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) ADD file:317a8c7f54c369601633fa49b1820a28778446f3a253eba0db0ef3fdb71461a4 in /\\"]}}"
      },
      {
         "v1Compatibility": "{\\"id\\":\\"3690474eb5b4b26fdfbd89c6e159e8cc376ca76ef48032a30fa6aafd56337880\\",\\"created\\":\\"2015-08-11T21:02:10.275489246Z\\",\\"container_config\\":{\\"Cmd\\":[\\"/bin/sh -c #(nop) MAINTAINER Jeff Lindsay \\\\u003cprogrium@gmail.com\\\\u003e\\"]}}"
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
        def cache = new Cache()
        def scanner = new ContainerScanner().withCache(cache).withLayerConfig(Paths.get(layerJson.absolutePath))

        when:
        def digest = scanner.resolveV1Manifest(MANIFEST, IMAGE_NAME)
        then:
        def entry = cache.get("/v2/$IMAGE_NAME/manifests/$digest")
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

    @Ignore
    def 'should resolve fastqc' () {
        given:
        def HOST = 'quay.io'
        def IMAGE = 'biocontainers/fastqc'
        def TAG = "0.11.9--0"
        and:
        def cache = new Cache()
        def client = new ProxyClient(HOST, IMAGE, new SimpleAuthProvider(
                username: Mock.QUAY_USER,
                password: Mock.QUAY_PAT,
                authUrl: Mock.QUAY_AUTH,
                service: HOST))
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
        def digest = scanner.resolve(IMAGE, TAG, headers)
        then:
        digest
    }
}
