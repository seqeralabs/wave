/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.core.spec

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class IndexSpecTest extends Specification {

    def 'should parse container index' () {
        given:
        def INDEX = '''
{
  "manifests" : [ {
    "annotations" : {
      "com.docker.official-images.bashbrew.arch" : "amd64",
      "org.opencontainers.image.base.name" : "scratch",
      "org.opencontainers.image.created" : "2024-09-11T15:32:52Z",
      "org.opencontainers.image.revision" : "5eb4e443534e093c4d8fe2b6761432430827cc95",
      "org.opencontainers.image.source" : "https://github.com/docker-library/busybox.git",
      "org.opencontainers.image.url" : "https://hub.docker.com/_/busybox",
      "org.opencontainers.image.version" : "1.36.1-musl"
    },
    "digest" : "sha256:9a53a254f289b4e9542024676c004f8d4810f785641f8bbbecce03dda576e037",
    "mediaType" : "application/vnd.oci.image.manifest.v1+json",
    "platform" : {
      "architecture" : "amd64",
      "os" : "linux"
    },
    "size" : 608
  },  {
    "annotations" : {
      "com.docker.official-images.bashbrew.arch" : "arm32v6",
      "org.opencontainers.image.base.name" : "scratch",
      "org.opencontainers.image.created" : "2024-09-11T15:32:52Z",
      "org.opencontainers.image.revision" : "f952f50fdb990288c88868e2dd4466bfee28bbfc",
      "org.opencontainers.image.source" : "https://github.com/docker-library/busybox.git",
      "org.opencontainers.image.url" : "https://hub.docker.com/_/busybox",
      "org.opencontainers.image.version" : "1.36.1-musl"
    },
    "digest" : "sha256:17934c55824eff3072923db6cd7c3ba00359e57b730e5581e7273c610738f910",
    "mediaType" : "application/vnd.oci.image.manifest.v1+json",
    "platform" : {
      "architecture" : "arm",
      "os" : "linux",
      "variant" : "v6"
    },
    "size" : 608
  }, {
    "annotations" : {
      "com.docker.official-images.bashbrew.arch" : "arm32v7",
      "org.opencontainers.image.base.name" : "scratch",
      "org.opencontainers.image.created" : "2024-09-11T15:32:52Z",
      "org.opencontainers.image.revision" : "9789898767be841976f1e28e0f89212db2de833d",
      "org.opencontainers.image.source" : "https://github.com/docker-library/busybox.git",
      "org.opencontainers.image.url" : "https://hub.docker.com/_/busybox",
      "org.opencontainers.image.version" : "1.36.1-musl"
    },
    "digest" : "sha256:c0d515df4d44c2a984c0aa8553e06b030a52c3800a6621e3ee393f3f42f8e995",
    "mediaType" : "application/vnd.oci.image.manifest.v1+json",
    "platform" : {
      "architecture" : "arm",
      "os" : "linux",
      "variant" : "v7"
    },
    "size" : 608
  },  {
    "annotations" : {
      "com.docker.official-images.bashbrew.arch" : "arm64v8",
      "org.opencontainers.image.base.name" : "scratch",
      "org.opencontainers.image.created" : "2024-09-11T15:32:52Z",
      "org.opencontainers.image.revision" : "9df05e09b53bcc7629ec0fc93242780ad29073b8",
      "org.opencontainers.image.source" : "https://github.com/docker-library/busybox.git",
      "org.opencontainers.image.url" : "https://hub.docker.com/_/busybox",
      "org.opencontainers.image.version" : "1.36.1-musl"
    },
    "digest" : "sha256:c6cdebd45a7b8fb6849484c6e079ed0db781d65bed367ae5804919ad3a4eda67",
    "mediaType" : "application/vnd.oci.image.manifest.v1+json",
    "platform" : {
      "architecture" : "arm64",
      "os" : "linux",
      "variant" : "v8"
    },
    "size" : 608
  },  {
    "annotations" : {
      "com.docker.official-images.bashbrew.arch" : "s390x",
      "org.opencontainers.image.base.name" : "scratch",
      "org.opencontainers.image.created" : "2024-09-11T15:32:52Z",
      "org.opencontainers.image.revision" : "6789229a68e5c3879f0eab31059f08466884e147",
      "org.opencontainers.image.source" : "https://github.com/docker-library/busybox.git",
      "org.opencontainers.image.url" : "https://hub.docker.com/_/busybox",
      "org.opencontainers.image.version" : "1.36.1-musl"
    },
    "digest" : "sha256:88a9b9e39c4f9b715a14b5cebe89bcbe16af930490b8aeb5632a003300568a09",
    "mediaType" : "application/vnd.oci.image.manifest.v1+json",
    "platform" : {
      "architecture" : "s390x",
      "os" : "linux"
    },
    "size" : 608
  } ],
  "mediaType" : "application/vnd.oci.image.index.v1+json",
  "schemaVersion" : 2
}
'''

        when:
        def spec = IndexSpec.parse(INDEX)
        then:
        spec.mediaType == "application/vnd.oci.image.index.v1+json"
        spec.schemaVersion == 2
        and:
        spec.manifests.size() == 5
        and:
        spec.manifests.first.size == 608
        spec.manifests.first.digest == "sha256:9a53a254f289b4e9542024676c004f8d4810f785641f8bbbecce03dda576e037"
        spec.manifests.first.mediaType == "application/vnd.oci.image.manifest.v1+json"
        spec.manifests.first.platform == new IndexSpec.PlatformSpec(architecture: "amd64",os:"linux")
    }

    // =============== PlatformSpec test ===============

    def "should validate equals and hash code" () {
        given:
        def p1 = new IndexSpec.PlatformSpec("amd64", "linux", "a")
        def p2 = new IndexSpec.PlatformSpec("amd64", "linux", "a")
        def p3 = new IndexSpec.PlatformSpec("amd64", "linux", "c")

        expect:
        p1 == p2
        p1 != p3
        and:
        p1.hashCode() == p2.hashCode()
        p1.hashCode() != p3.hashCode()
    }

    def 'should create from a map' () {
        when:
        def spec = IndexSpec.PlatformSpec.of(architecture: 'amd64', os:'linux', variant: 'xyz')
        then:
        spec.architecture == "amd64"
        spec.os == "linux"
        spec.variant == "xyz"
    }

    // =============== ManifestSpec test ===============

    def "should create manifest from map" () {
        given:
        def manifest = [mediaType:'bar', digest: 'sha256:67890', size:222L, annotations: [foo:'1', bar:'2'], platform: [architecture: "amd64", os:"linux", variant:"v8"]]

        when:
        def ref = IndexSpec.ManifestSpec.of(manifest)
        then:
        ref.mediaType == "bar"
        ref.digest == "sha256:67890"
        ref.size == 222L
        ref.annotations == [foo:'1', bar:'2']
        ref.platform == IndexSpec.PlatformSpec.of(architecture: "amd64", os:"linux", variant:"v8")
    }

    def "should create from list" () {
        given:
        def list = [
                [mediaType:'foo', digest: 'sha256:12345', size:111L, platform: [architecture: "arm64", os:"linux", variant:"v6"]],
                [mediaType:'bar', digest: 'sha256:67890', size:222L, annotations: [foo:'1', bar:'2'], platform: [architecture: "amd64", os:"linux", variant:"v8"]]
        ]

        when:
        def manifests = IndexSpec.ManifestSpec.of(list)
        then:
        manifests.size() == 2
        and:
        manifests[0].mediaType == "foo"
        manifests[0].digest == "sha256:12345"
        manifests[0].size == 111L
        manifests[0].platform == IndexSpec.PlatformSpec.of(architecture: "arm64", os:"linux", variant:"v6")
        and:
        manifests[1].mediaType == "bar"
        manifests[1].digest == "sha256:67890"
        manifests[1].size == 222L
        manifests[1].platform ==  IndexSpec.PlatformSpec.of(architecture: "amd64", os:"linux", variant:"v8")
        manifests[1].annotations == [foo:'1', bar:'2']
    }
}
