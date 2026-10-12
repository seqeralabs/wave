/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.builder

import spock.lang.Specification

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.RegHelper

import static io.seqera.wave.model.ContentType.DOCKER_MANIFEST_V2_TYPE
import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1

class ManifestAssemblerTest extends Specification {

    /**
     * The manifest uploaded by `singularity push` — a Docker v2 manifest wrapping a SIF layer
     */
    static final String SIF_DOCKER_V2_MANIFEST = '''\
        {
          "schemaVersion": 2,
          "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
          "config": {
            "mediaType": "application/vnd.sylabs.sif.config.v1+json",
            "size": 2,
            "digest": "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a"
          },
          "layers": [
            {
              "mediaType": "application/vnd.sylabs.sif.layer.v1.sif",
              "size": 684818432,
              "digest": "sha256:5566d3ac0f4d78a2b9b3bd9ff4b86c4ba8de0d5e2e1f6e6f4a2c0a1b2c3d4e5f",
              "annotations": { "org.opencontainers.image.title": "image.sif" }
            }
          ]
        }
        '''.stripIndent()

    def 'should build OCI image index JSON'() {
        given:
        def manifests = [
            [
                mediaType: OCI_IMAGE_MANIFEST_V1,
                digest: 'sha256:aaa111',
                size: 1234,
                platform: [architecture: 'amd64', os: 'linux']
            ],
            [
                mediaType: OCI_IMAGE_MANIFEST_V1,
                digest: 'sha256:bbb222',
                size: 5678,
                platform: [architecture: 'arm64', os: 'linux']
            ]
        ]

        when:
        def json = ManifestAssembler.buildImageIndex(manifests)
        def parsed = new JsonSlurper().parseText(json)

        then:
        parsed.schemaVersion == 2
        parsed.mediaType == OCI_IMAGE_INDEX_V1
        parsed.manifests.size() == 2

        and:
        parsed.manifests[0].mediaType == OCI_IMAGE_MANIFEST_V1
        parsed.manifests[0].digest == 'sha256:aaa111'
        parsed.manifests[0].size == 1234
        parsed.manifests[0].platform.architecture == 'amd64'
        parsed.manifests[0].platform.os == 'linux'

        and:
        parsed.manifests[1].mediaType == OCI_IMAGE_MANIFEST_V1
        parsed.manifests[1].digest == 'sha256:bbb222'
        parsed.manifests[1].size == 5678
        parsed.manifests[1].platform.architecture == 'arm64'
        parsed.manifests[1].platform.os == 'linux'
    }

    def 'should build valid JSON with single manifest'() {
        given:
        def manifests = [
            [
                mediaType: 'application/vnd.docker.distribution.manifest.v2+json',
                digest: 'sha256:deadbeef',
                size: 999,
                platform: [architecture: 'amd64', os: 'linux']
            ]
        ]

        when:
        def json = ManifestAssembler.buildImageIndex(manifests)
        def parsed = new JsonSlurper().parseText(json)

        then:
        parsed.schemaVersion == 2
        parsed.manifests.size() == 1
        parsed.manifests[0].digest == 'sha256:deadbeef'
    }

    def 'should not include the manifest body in the image index'() {
        given:
        def manifests = [
            [
                mediaType: OCI_IMAGE_MANIFEST_V1,
                digest: 'sha256:aaa111',
                size: 1234,
                platform: [architecture: 'amd64', os: 'linux'],
                body: '{"schemaVersion":2}'.bytes
            ]
        ]

        when:
        def json = ManifestAssembler.buildImageIndex(manifests)
        def parsed = new JsonSlurper().parseText(json)

        then:
        !json.contains('body')
        parsed.manifests[0].keySet() == ['mediaType', 'digest', 'size', 'platform'] as Set
    }

    def 'should rewrite a docker v2 manifest as an OCI manifest preserving config, layers and annotations'() {
        given:
        def body = SIF_DOCKER_V2_MANIFEST.bytes

        when:
        def result = ManifestAssembler.rewriteAsOciManifest(body)
        def parsed = new JsonSlurper().parse(result)

        then: 'only the media type is changed'
        parsed.mediaType == OCI_IMAGE_MANIFEST_V1
        parsed.schemaVersion == 2

        and: 'the config descriptor is preserved verbatim'
        parsed.config.mediaType == 'application/vnd.sylabs.sif.config.v1+json'
        parsed.config.size == 2
        parsed.config.digest == 'sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a'

        and: 'the layers and their annotations are preserved verbatim'
        parsed.layers.size() == 1
        parsed.layers[0].mediaType == 'application/vnd.sylabs.sif.layer.v1.sif'
        parsed.layers[0].size == 684818432
        parsed.layers[0].digest == 'sha256:5566d3ac0f4d78a2b9b3bd9ff4b86c4ba8de0d5e2e1f6e6f4a2c0a1b2c3d4e5f'
        parsed.layers[0].annotations['org.opencontainers.image.title'] == 'image.sif'

        and: 'the rewritten manifest has a different digest'
        RegHelper.digest(result) != RegHelper.digest(body)
    }

    def 'should preserve subject and manifest level annotations when rewriting'() {
        given:
        def body = JsonOutput.toJson([
                schemaVersion: 2,
                mediaType: DOCKER_MANIFEST_V2_TYPE,
                config: [mediaType: 'application/vnd.sylabs.sif.config.v1+json', size: 2, digest: 'sha256:aaa'],
                layers: [[mediaType: 'application/vnd.sylabs.sif.layer.v1.sif', size: 10, digest: 'sha256:bbb']],
                subject: [mediaType: OCI_IMAGE_MANIFEST_V1, size: 5, digest: 'sha256:ccc'],
                annotations: ['org.opencontainers.image.created': '2024-01-01T00:00:00Z']
        ]).bytes

        when:
        def parsed = new JsonSlurper().parse(ManifestAssembler.rewriteAsOciManifest(body))

        then:
        parsed.mediaType == OCI_IMAGE_MANIFEST_V1
        parsed.subject.digest == 'sha256:ccc'
        parsed.annotations['org.opencontainers.image.created'] == '2024-01-01T00:00:00Z'
    }

    def 'should normalise a docker v2 child manifest and push it as an OCI manifest'() {
        given:
        def body = SIF_DOCKER_V2_MANIFEST.bytes
        def expectedBytes = ManifestAssembler.rewriteAsOciManifest(body)
        def expectedDigest = RegHelper.digest(expectedBytes)
        and:
        def assembler = Spy(ManifestAssembler)
        def descriptor = [
                mediaType: DOCKER_MANIFEST_V2_TYPE,
                digest: 'sha256:original',
                size: body.length,
                platform: [architecture: 'arm64', os: 'linux'],
                body: body
        ]

        when:
        def result = assembler.normalizePlatformManifest('docker.io/foo/bar:arm64', descriptor, PlatformId.NULL)

        then: 'the rewritten manifest is pushed by digest against the child image'
        1 * assembler.pushOciManifest('docker.io/foo/bar:arm64', { byte[] it -> RegHelper.digest(it) == expectedDigest }, expectedDigest, PlatformId.NULL) >> null

        and: 'the index descriptor points at the new OCI manifest'
        result.mediaType == OCI_IMAGE_MANIFEST_V1
        result.digest == expectedDigest
        result.size == expectedBytes.length
        result.platform == [architecture: 'arm64', os: 'linux']
    }

    def 'should pass through an OCI child manifest without pushing anything'() {
        given:
        def assembler = Spy(ManifestAssembler)
        def descriptor = [
                mediaType: OCI_IMAGE_MANIFEST_V1,
                digest: 'sha256:aaa111',
                size: 1234,
                platform: [architecture: 'amd64', os: 'linux'],
                body: '{"schemaVersion":2}'.bytes
        ]

        when:
        def result = assembler.normalizePlatformManifest('docker.io/foo/bar:amd64', descriptor, PlatformId.NULL)

        then:
        0 * assembler.pushOciManifest(_, _, _, _)
        and:
        result.is(descriptor)
    }
}
