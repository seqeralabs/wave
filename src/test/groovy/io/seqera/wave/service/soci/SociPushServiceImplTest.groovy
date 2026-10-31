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

import java.time.Duration
import java.util.concurrent.ExecutorService

import groovy.json.JsonSlurper
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.tower.PlatformId
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SociPushServiceImplTest extends Specification {

    static final String INDEX = 'sha256:aa943ce411423c7582288f18707ff66eddcbaeb510502d747a72aeb34f30d159'
    static final String AMD64 = 'sha256:1148dbb014615300fa7e713b2f94f550c0d1c69780c0805f84f093ceee34dd0b'
    static final String LAYER1 = 'sha256:6fc242d221a673263eabd14a9ad9584d3177c96e5cb504c891497338e2f912f5'
    static final String LAYER3 = 'sha256:f574edac59d9f092df596f54e5b52656c803fd3ae30825b8ee5a189ed05f63d3'
    static final String GZIP = 'application/vnd.oci.image.layer.v1.tar+gzip'

    SociConfig config = new SociConfig(spanSize: 4194304, minLayerSize: 10485760, repo: 'public.cr.stage-seqera.io/wave/soci-ztoc',
            pendingCacheDuration: Duration.ofSeconds(30), publishTimeout: Duration.ofSeconds(5), statusDelay: Duration.ofMillis(10), jobTimeout: Duration.ofMinutes(1))

    static String resource(String name) {
        SociPushServiceImplTest.getResourceAsStream("/soci/$name").text
    }

    static Map<String,ZtocEntry> ztocs() {
        return [
                (LAYER1): new ZtocEntry(LAYER1, GZIP, 'sha256:901e7c42665c70adfe9bf36bc26e4a80d006f3cc96917d43f1596fff778ffae4', 1224072, 4194304),
                (LAYER3): new ZtocEntry(LAYER3, GZIP, 'sha256:dcd11d24b6a61b29253739662ece4ab0e5af0cd39c9f9cf77a7d8f96fc5421c0', 640400, 4194304),
        ]
    }

    def 'should create the soci image name' () {
        expect:
        SociPushServiceImpl.sociImage(IMAGE) == EXPECTED

        where:
        IMAGE                               | EXPECTED
        'quay.io/user/app:1.0'              | 'quay.io/user/app:1.0-soci'
        'docker.io/library/ubuntu:24.04'    | 'docker.io/library/ubuntu:24.04-soci'
        'ubuntu'                            | 'docker.io/library/ubuntu:latest-soci'
    }

    def 'should return soci tag when it exists' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def store = Mock(SociPushStateStore)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy, store: store)

        when:
        def result = service.sociTarget('quay.io/user/app:1.0', ContainerPlatform.of('linux/amd64'), PlatformId.NULL)
        then:
        1 * proxy.getManifestContent('quay.io/user/app:1.0-soci', PlatformId.NULL) >> '{}'
        0 * store.putIfAbsent(_, _)
        result == 'quay.io/user/app:1.0-soci'
    }

    def 'should return original tag and schedule push when soci tag missing' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def store = Mock(SociPushStateStore)
        def executor = Mock(ExecutorService)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy, store: store, ioExecutor: executor)

        when:
        def result = service.sociTarget('quay.io/user/app:1.0', ContainerPlatform.of('linux/amd64'), PlatformId.NULL)
        then:
        1 * proxy.getManifestContent('quay.io/user/app:1.0-soci', PlatformId.NULL) >> null
        1 * store.putIfAbsent('quay.io/user/app:1.0-soci', _) >> PUT
        (PUT ? 1 : 0) * executor.execute(_)
        result == 'quay.io/user/app:1.0'

        where:
        PUT << [true, false]
    }

    def 'should prepare the soci documents once the layers are indexed' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def index = Mock(SociIndexService)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy, indexService: index)

        when:
        def result = service.prepare('quay.io/user/python:3.12', ContainerPlatform.of('linux/amd64'), PlatformId.NULL)
        then:
        1 * proxy.getManifestContent('quay.io/user/python:3.12', PlatformId.NULL) >> resource('python-3.12-slim-index.json')
        1 * proxy.getManifestContent("quay.io/user/python@$AMD64", PlatformId.NULL) >> resource('python-3.12-slim-amd64-manifest.json')
        1 * index.indexLayers('quay.io/user/python', { List<Map> it -> it*.digest == [LAYER1, LAYER3] }, PlatformId.NULL)
        1 * index.findZtocs([LAYER1, LAYER3]) >> ztocs()
        and:
        def json = new JsonSlurper().parseText(result.index) as Map
        (json.manifests as List<Map>).last().artifactType == 'application/vnd.amazon.soci.index.v2+json'
        result.docs.size() == 1
    }

    def 'should wait for the missing ztocs' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def index = Mock(SociIndexService)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy, indexService: index)

        when:
        def result = service.awaitZtocs('quay.io/user/python', [[digest: LAYER1], [digest: LAYER3]], PlatformId.NULL, java.time.Instant.now().plusSeconds(5))
        then:
        3 * index.findZtocs([LAYER1, LAYER3]) >>> [[:], ztocs().subMap([LAYER1]), ztocs()]
        3 * index.indexLayers('quay.io/user/python', _, PlatformId.NULL)
        result == ztocs()
    }

    def 'should build push plan in order S, A2, index' () {
        given:
        def service = new SociPushServiceImpl(config: config)
        def docs = SociManifestAssembler.assemble(resource('python-3.12-slim-amd64-manifest.json'), ztocs(), 4194304)
        def prepared = new SociPushServiceImpl.Prepared(index: SociManifestAssembler.updateIndex(resource('python-3.12-slim-index.json'), [(AMD64): docs]), docs: [docs])

        when:
        def plan = new JsonSlurper().parseText(service.pushPlan(prepared, 'quay.io/user/python:3.12-soci')) as Map
        then:
        plan.blobs*.digest == [SociManifestAssembler.EMPTY_JSON_DIGEST] + ztocs().values()*.ztocDigest
        plan.blobs[1].source == 'docker://public.cr.stage-seqera.io/wave/soci-ztoc@sha256:901e7c42665c70adfe9bf36bc26e4a80d006f3cc96917d43f1596fff778ffae4'
        plan.blobs[0].source == 'base64:' + '{}'.bytes.encodeBase64().toString()
        and: 'the original tag is never referenced'
        plan.manifests*.reference == [docs.sociIndexDigest, docs.manifestDigest, '3.12-soci']
        new String(plan.manifests[2].body.decodeBase64()) == prepared.index
    }
}
