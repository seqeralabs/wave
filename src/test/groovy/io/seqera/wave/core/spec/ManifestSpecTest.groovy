package io.seqera.wave.core.spec

import spock.lang.Specification

import io.seqera.wave.model.ContentType

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ManifestSpecTest extends Specification {

    def 'should parse manifest spec v2' () {
        given:
        def SPEC = '''
            {
               "schemaVersion":2,
               "mediaType":"application/vnd.oci.image.manifest.v1+json",
               "config":{
                  "mediaType":"application/vnd.oci.image.config.v1+json",
                  "digest":"sha256:3f57d9401f8d42f986df300f0c69192fc41da28ccc8d797829467780db3dd741",
                  "size":581
               },
               "layers":[
                  {
                     "mediaType":"application/vnd.oci.image.layer.v1.tar+gzip",
                     "digest":"sha256:9ad63333ebc97e32b987ae66aa3cff81300e4c2e6d2f2395cef8a3ae18b249fe",
                     "size":2220094
                  }
               ],
               "annotations":{
                  "org.opencontainers.image.revision":"09ee80aedec1d8c604f104e8bec41ed19274620a",
                  "org.opencontainers.image.source":"https://github.com/docker-library/busybox.git#09ee80aedec1d8c604f104e8bec41ed19274620a:latest/glibc",
                  "org.opencontainers.image.url":"https://hub.docker.com/_/busybox",
                  "org.opencontainers.image.version":"1.36.1-glibc"
               }
            }
            '''

        when:
        def result = ManifestSpec.of(SPEC)
        then:
        result.schemaVersion == 2
        result.mediaType == 'application/vnd.oci.image.manifest.v1+json'
        and:
        result.config.mediaType == 'application/vnd.oci.image.config.v1+json'
        result.config.digest == 'sha256:3f57d9401f8d42f986df300f0c69192fc41da28ccc8d797829467780db3dd741'
        result.config.size == 581
        and:
        result.layers[0].mediaType == 'application/vnd.oci.image.layer.v1.tar+gzip'
        result.layers[0].digest == 'sha256:9ad63333ebc97e32b987ae66aa3cff81300e4c2e6d2f2395cef8a3ae18b249fe'
        result.layers[0].size == 2220094
        and:
        result.annotations.'org.opencontainers.image.revision' == '09ee80aedec1d8c604f104e8bec41ed19274620a'
        result.annotations.'org.opencontainers.image.source' == 'https://github.com/docker-library/busybox.git#09ee80aedec1d8c604f104e8bec41ed19274620a:latest/glibc'
        result.annotations.'org.opencontainers.image.url' == 'https://hub.docker.com/_/busybox'
        result.annotations.'org.opencontainers.image.version' == '1.36.1-glibc'
    }

    def 'should parse manifest spec v1' () {
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

        when:
        def spec = ManifestSpec.parseV1(MANIFEST)
        then:
        spec.schemaVersion == 1
        spec.mediaType == ContentType.DOCKER_MANIFEST_V1_JWS_TYPE
        spec.config == null
        spec.layers == [
                ObjectRef.of(digest:'sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4'),
                ObjectRef.of(digest:'sha256:6d92b3a49ebfad5fe895550c2cb24b6370d61783aa4f979702a94892cbd19077')
        ]
    }
}
