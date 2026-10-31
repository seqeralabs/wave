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

package io.seqera.wave.core

import spock.lang.Specification

import java.net.http.HttpResponse

import groovy.json.JsonSlurper
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.service.soci.SociIndexService
import io.seqera.wave.service.soci.ZtocEntry
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.DigestStoreFactory
import io.seqera.wave.storage.DockerDigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.storage.ZippedDigestStore
import io.seqera.wave.tower.PlatformId
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SociInjectorTest extends Specification {

    static final String INDEX = 'sha256:aa943ce411423c7582288f18707ff66eddcbaeb510502d747a72aeb34f30d159'
    static final String AMD64 = 'sha256:1148dbb014615300fa7e713b2f94f550c0d1c69780c0805f84f093ceee34dd0b'
    static final String LAYER1 = 'sha256:6fc242d221a673263eabd14a9ad9584d3177c96e5cb504c891497338e2f912f5'
    static final String LAYER3 = 'sha256:f574edac59d9f092df596f54e5b52656c803fd3ae30825b8ee5a189ed05f63d3'
    static final String GZIP = 'application/vnd.oci.image.layer.v1.tar+gzip'
    static final String PREFIX = 'docker.io/v2/library/python'

    SociConfig config = new SociConfig(minLayerSize: 10485760, repo: 'public.cr.stage-seqera.io/wave/soci-ztoc')

    static String resource(String name) {
        SociInjectorTest.getResourceAsStream("/soci/$name").text
    }

    static Map<String,ZtocEntry> ztocs() {
        return [
                (LAYER1): new ZtocEntry(LAYER1, GZIP, 'sha256:901e7c42665c70adfe9bf36bc26e4a80d006f3cc96917d43f1596fff778ffae4', 1224072, 4194304),
                (LAYER3): new ZtocEntry(LAYER3, GZIP, 'sha256:dcd11d24b6a61b29253739662ece4ab0e5af0cd39c9f9cf77a7d8f96fc5421c0', 640400, 4194304),
        ]
    }

    /** a map-backed storage */
    static class MapStorage implements Storage {
        Map<String,DigestStore> entries = [:]
        Optional<DigestStore> getManifest(String path) { Optional.ofNullable(entries[path]) }
        DigestStore saveManifest(String path, String manifest, String type, String digest) { entries[path] = ZippedDigestStore.fromUncompressed(manifest.bytes, type, digest, manifest.bytes.length) }
        DigestStore saveManifest(String path, DigestStore store) { entries[path] = store }
        Optional<DigestStore> getBlob(String path) { Optional.ofNullable(entries[path]) }
        DigestStore saveBlob(String path, byte[] content, String type, String digest) { entries[path] = ZippedDigestStore.fromUncompressed(content, type, digest, content.length) }
        DigestStore saveBlob(String path, ContainerLayer layer) { entries[path] = DigestStoreFactory.of(layer) }
    }

    ProxyClient client() {
        Mock(ProxyClient) { getRegistry() >> new RegistryInfo('docker.io', new URI('https://registry-1.docker.io'), null) }
    }

    MapStorage storage() {
        def storage = new MapStorage()
        storage.saveManifest("$PREFIX/manifests/$INDEX", resource('python-3.12-slim-index.json'), 'application/vnd.oci.image.index.v1+json', INDEX)
        storage.saveManifest("$PREFIX/manifests/$AMD64", resource('python-3.12-slim-amd64-manifest.json'), 'application/vnd.oci.image.manifest.v1+json', AMD64)
        return storage
    }

    def 'should inject soci docs into the image index when ztocs complete' () {
        given:
        def storage = storage()
        def service = Mock(SociIndexService)
        def injector = new SociInjector(client(), storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), null)

        when:
        def result = injector.apply('library/python', new ContainerDigestPair(INDEX, INDEX), [:])

        then:
        1 * service.findZtocs([LAYER1, LAYER3]) >> ztocs()
        0 * service.indexLayers(_, _, _)
        and:
        result.source == INDEX
        result.target != INDEX
        and: 'the index served for the target digest has the soci index descriptor'
        def index = new JsonSlurper().parseText(new String(storage.entries["$PREFIX/manifests/${result.target}"].bytes)) as Map
        def soci = (index.manifests as List<Map>).last()
        soci.artifactType == 'application/vnd.amazon.soci.index.v2+json'
        def image = (index.manifests as List<Map>).find { it.digest == soci.annotations['com.amazon.soci.image-manifest-digest'] }
        image.annotations['com.amazon.soci.index-digest'] == soci.digest
        and: 'the annotated manifest, the soci index manifest and its blobs are served by wave'
        storage.entries["$PREFIX/manifests/${image.digest}"] != null
        storage.entries["$PREFIX/manifests/${soci.digest}"] != null
        new String(storage.entries["$PREFIX/blobs/sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a"].bytes) == '{}'
        def ztoc = storage.entries["$PREFIX/blobs/sha256:901e7c42665c70adfe9bf36bc26e4a80d006f3cc96917d43f1596fff778ffae4"] as DockerDigestStore
        ztoc.location == 'docker://public.cr.stage-seqera.io/v2/wave/soci-ztoc/blobs/sha256:901e7c42665c70adfe9bf36bc26e4a80d006f3cc96917d43f1596fff778ffae4'
        ztoc.size == 1224072
    }

    def 'should index the missing layers and await them' () {
        given:
        def storage = storage()
        def service = Mock(SociIndexService)
        def injector = new SociInjector(client(), storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), null)

        when:
        def result = injector.apply('library/python', new ContainerDigestPair(INDEX, INDEX), [:])

        then:
        1 * service.findZtocs([LAYER1, LAYER3]) >> ztocs().subMap([LAYER1])
        then:
        1 * service.indexLayers('docker.io/library/python', { List<Map> it -> it*.digest == [LAYER3] }, PlatformId.NULL)
        then:
        1 * service.awaitLayers([LAYER3], _) >> true
        then:
        1 * service.findZtocs([LAYER1, LAYER3]) >> ztocs()
        and:
        result.source == INDEX
        result.target != INDEX
    }

    def 'should serve the container as it is when some layer is not indexed' () {
        given:
        def storage = storage()
        def service = Mock(SociIndexService)
        def injector = new SociInjector(client(), storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), null)

        when:
        def result = injector.apply('library/python', new ContainerDigestPair(INDEX, INDEX), [:])

        then:
        2 * service.findZtocs([LAYER1, LAYER3]) >> ztocs().subMap([LAYER1])
        1 * service.indexLayers('docker.io/library/python', _, PlatformId.NULL)
        1 * service.awaitLayers([LAYER3], _) >> COMPLETED
        and:
        result == new ContainerDigestPair(INDEX, INDEX)

        where:
        COMPLETED << [true, false]
    }

    def 'should serve the container as it is when the soci index fails' () {
        given:
        def storage = storage()
        def service = Mock(SociIndexService) { findZtocs(_) >> { throw new IllegalStateException('registry error') } }
        def injector = new SociInjector(client(), storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), null)

        expect:
        injector.apply('library/python', new ContainerDigestPair(INDEX, INDEX), [:]) == new ContainerDigestPair(INDEX, INDEX)
    }

    def 'should not index the layers added by wave' () {
        given:
        def storage = storage()
        def service = Mock(SociIndexService)
        def injector = new SociInjector(client(), storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), [LAYER3] as Set)

        when:
        def result = injector.apply('library/python', new ContainerDigestPair(INDEX, INDEX), [:])

        then:
        1 * service.findZtocs([LAYER1]) >> ztocs().subMap([LAYER1])
        0 * service.indexLayers(_, _, _)
        and:
        result.target != INDEX
    }

    def 'should wrap a single manifest in an index' () {
        given:
        def storage = storage()
        storage.saveBlob("$PREFIX/blobs/sha256:ae4e8b7c575fabea88a029b6cae757f342ca2525e07f7c22272d5105c346b35e", '{"architecture":"amd64","os":"linux"}'.bytes, 'application/vnd.oci.image.config.v1+json', 'sha256:ae4e8b7c575fabea88a029b6cae757f342ca2525e07f7c22272d5105c346b35e')
        def service = Mock(SociIndexService) { findZtocs(_) >> ztocs() }
        def injector = new SociInjector(client(), storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), null)

        when:
        def result = injector.apply('library/python', new ContainerDigestPair(AMD64, AMD64), [:])
        def index = new JsonSlurper().parseText(new String(storage.entries["$PREFIX/manifests/${result.target}"].bytes)) as Map

        then:
        index.mediaType == 'application/vnd.oci.image.index.v1+json'
        index.manifests*.platform == [[architecture: 'amd64', os: 'linux'], [architecture: 'amd64', os: 'linux']]
        index.manifests[1].artifactType == 'application/vnd.amazon.soci.index.v2+json'
    }

    def 'should leave an image already having a soci index unchanged' () {
        given:
        def storage = new MapStorage()
        def digest = 'sha256:1e8a241f53179b7560ddf1ab8bf44d59aad9bd522ded625c5f31014199783791'
        storage.saveManifest("$PREFIX/manifests/$digest", resource('python-3.12-slim-soci-index.json'), 'application/vnd.oci.image.index.v1+json', digest)
        def service = Mock(SociIndexService)
        def injector = new SociInjector(client(), storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), null)

        when:
        def result = injector.apply('library/python', new ContainerDigestPair(digest, digest), [:])
        then:
        0 * service._
        result == new ContainerDigestPair(digest, digest)
    }

    def 'should fetch the platform manifest from upstream when not in storage' () {
        given:
        def storage = new MapStorage()
        storage.saveManifest("$PREFIX/manifests/$INDEX", resource('python-3.12-slim-index.json'), 'application/vnd.oci.image.index.v1+json', INDEX)
        def client = client()
        def resp = Mock(HttpResponse) { statusCode() >> 200; body() >> resource('python-3.12-slim-amd64-manifest.json') }
        def service = Mock(SociIndexService)
        def injector = new SociInjector(client, storage, service, config, PlatformId.NULL, ContainerPlatform.of('linux/amd64'), null)

        when:
        def result = injector.apply('library/python', new ContainerDigestPair(INDEX, INDEX), [:])
        then:
        1 * client.getString("/v2/library/python/manifests/$AMD64", [:]) >> resp
        1 * service.findZtocs(_) >> ztocs()
        result.target != INDEX
    }
}
