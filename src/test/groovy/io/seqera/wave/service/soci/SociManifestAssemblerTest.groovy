/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.soci

import spock.lang.Specification

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import io.seqera.wave.util.RegHelper
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SociManifestAssemblerTest extends Specification {

    static final String LAYER1 = 'sha256:6fc242d221a673263eabd14a9ad9584d3177c96e5cb504c891497338e2f912f5'
    static final String LAYER3 = 'sha256:f574edac59d9f092df596f54e5b52656c803fd3ae30825b8ee5a189ed05f63d3'
    static final String GZIP = 'application/vnd.oci.image.layer.v1.tar+gzip'

    static String resource(String name) {
        SociManifestAssemblerTest.getResourceAsStream("/soci/$name").text
    }

    /** the zTOCs of the golden image, as built by 'soci convert' */
    static Map<String,ZtocEntry> goldenZtocs() {
        return [
                (LAYER1): new ZtocEntry(LAYER1, GZIP, 'sha256:901e7c42665c70adfe9bf36bc26e4a80d006f3cc96917d43f1596fff778ffae4', 1224072, 4194304),
                (LAYER3): new ZtocEntry(LAYER3, GZIP, 'sha256:dcd11d24b6a61b29253739662ece4ab0e5af0cd39c9f9cf77a7d8f96fc5421c0', 640400, 4194304),
        ]
    }

    def 'should produce soci index manifest matching soci convert' () {
        when:
        def docs = SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), goldenZtocs(), 4194304, 'AWS SOCI CLI v0.2')
        then: 'same bytes, and therefore the same digest, as soci convert'
        docs.sociIndexJson == resource('python-3.12-slim-soci-amd64-soci-index.json')
        docs.sociIndexDigest == 'sha256:6407e3cf3af08de9d7737b38c0937f341545df0e855712789d55d3afc4c5a738'
        docs.sociIndexSize == docs.sociIndexJson.bytes.length
        and:
        docs.configJson == '{}'
        docs.ztocs*.ztocDigest == goldenZtocs().values()*.ztocDigest
    }

    def 'should annotate manifest with index digest' () {
        when:
        def docs = SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), goldenZtocs(), 4194304, 'AWS SOCI CLI v0.2')
        def manifest = new JsonSlurper().parseText(docs.manifestJson) as Map
        def original = new JsonSlurper().parseText(resource('python-3.12-slim-amd64-manifest.json')) as Map
        then:
        manifest.annotations['com.amazon.soci.index-digest'] == docs.sociIndexDigest
        manifest.layers == original.layers
        manifest.config == original.config
        and: 'same bytes, and therefore the same digest, as soci convert'
        docs.manifestJson == resource('python-3.12-slim-soci-amd64-manifest.json')
        docs.manifestDigest == 'sha256:28a292e5fcff9df5a710520064a6d406a13a856694d445710af983cbf2d5ef3c'
    }

    def 'should append soci descriptor last with image-manifest-digest' () {
        given:
        def docs = SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), goldenZtocs(), 4194304)
        def original = 'sha256:1148dbb014615300fa7e713b2f94f550c0d1c69780c0805f84f093ceee34dd0b'

        when:
        def index = new JsonSlurper().parseText(SociManifestAssembler.updateIndex(resource('python-3.12-slim-index.json'), [(original): docs])) as Map
        def entries = index.manifests as List<Map>
        then:
        index.mediaType == 'application/vnd.oci.image.index.v1+json'
        entries.size() == 17
        and: 'the image entry points to the annotated manifest'
        def image = entries.find { it.digest == docs.manifestDigest }
        image.size == docs.manifestSize
        image.mediaType == 'application/vnd.oci.image.manifest.v1+json'
        image.annotations['com.amazon.soci.index-digest'] == docs.sociIndexDigest
        and: 'the soci entry is the last one'
        def soci = entries.last()
        soci.digest == docs.sociIndexDigest
        soci.size == docs.sociIndexSize
        soci.artifactType == 'application/vnd.amazon.soci.index.v2+json'
        soci.mediaType == 'application/vnd.oci.image.manifest.v1+json'
        soci.platform == [architecture: 'amd64', os: 'linux']
        soci.annotations == ['com.amazon.soci.image-manifest-digest': docs.manifestDigest]
    }

    def 'should leave other platforms untouched' () {
        given:
        def docs = SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), goldenZtocs(), 4194304)
        def original = new JsonSlurper().parseText(resource('python-3.12-slim-index.json')) as Map
        def amd64 = 'sha256:1148dbb014615300fa7e713b2f94f550c0d1c69780c0805f84f093ceee34dd0b'

        when:
        def index = new JsonSlurper().parseText(SociManifestAssembler.updateIndex(resource('python-3.12-slim-index.json'), [(amd64): docs])) as Map
        then:
        (index.manifests as List<Map>).subList(1,16) == (original.manifests as List<Map>).subList(1,16)
    }

    def 'should convert docker media types to oci' () {
        given:
        def manifest = [
                schemaVersion: 2,
                mediaType: 'application/vnd.docker.distribution.manifest.v2+json',
                config: [mediaType: 'application/vnd.docker.container.image.v1+json', digest: 'sha256:c0', size: 10],
                layers: [[mediaType: 'application/vnd.docker.image.rootfs.diff.tar.gzip', digest: LAYER1, size: 20000000]]
        ]
        def ztocs = [(LAYER1): new ZtocEntry(LAYER1, 'application/vnd.docker.image.rootfs.diff.tar.gzip', 'sha256:z1', 1000, 4194304)]
        def list = [
                schemaVersion: 2,
                mediaType: 'application/vnd.docker.distribution.manifest.list.v2+json',
                manifests: [[mediaType: 'application/vnd.docker.distribution.manifest.v2+json', digest: 'sha256:m1', size: 100, platform: [architecture: 'amd64', os: 'linux']]]
        ]

        when:
        def docs = SociManifestAssembler.assemble(JsonOutput.toJson(manifest), ztocs, 4194304)
        def result = new JsonSlurper().parseText(docs.manifestJson) as Map
        def index = new JsonSlurper().parseText(SociManifestAssembler.updateIndex(JsonOutput.toJson(list), ['sha256:m1': docs])) as Map
        then: 'manifest, config and index use oci media types, layers are left as they are, like soci convert does'
        result.mediaType == 'application/vnd.oci.image.manifest.v1+json'
        result.config.mediaType == 'application/vnd.oci.image.config.v1+json'
        result.layers[0].mediaType == 'application/vnd.docker.image.rootfs.diff.tar.gzip'
        index.mediaType == 'application/vnd.oci.image.index.v1+json'
        index.manifests[0].mediaType == 'application/vnd.oci.image.manifest.v1+json'
    }

    def 'should wrap single manifest in an index' () {
        given:
        def docs = SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), goldenZtocs(), 4194304)

        when:
        def index = new JsonSlurper().parseText(SociManifestAssembler.wrapInIndex(docs, [architecture: 'amd64', os: 'linux'])) as Map
        then:
        index.mediaType == 'application/vnd.oci.image.index.v1+json'
        index.manifests*.digest == [docs.manifestDigest, docs.sociIndexDigest]
        index.manifests[1].artifactType == 'application/vnd.amazon.soci.index.v2+json'
    }

    def 'should return null when no eligible layers' () {
        expect:
        SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), [:], 4194304) == null
    }

    def 'should find eligible layers' () {
        when:
        def layers = SociManifestAssembler.eligibleLayers(resource('python-3.12-slim-amd64-manifest.json'), 10485760)
        then:
        layers*.digest == [LAYER1, LAYER3]
        layers*.mediaType == [GZIP, GZIP]
        layers*.size == [29855236, 12121979]
    }

    def 'should detect an image already having a soci index' () {
        expect:
        SociManifestAssembler.hasSociIndex(resource('python-3.12-slim-soci-index.json'))
        !SociManifestAssembler.hasSociIndex(resource('python-3.12-slim-index.json'))
    }

    def 'should compute digests of the documents' () {
        when:
        def docs = SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), goldenZtocs(), 4194304)
        then:
        docs.manifestDigest == RegHelper.digest(docs.manifestJson)
        docs.sociIndexDigest == RegHelper.digest(docs.sociIndexJson)
        docs.configDigest == 'sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a'
    }
}
