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

import groovy.json.JsonSlurper

import static io.seqera.wave.model.ContentType.OCI_IMAGE_INDEX_V1
import static io.seqera.wave.model.ContentType.OCI_IMAGE_MANIFEST_V1

class ManifestAssemblerTest extends Specification {

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
}
