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

import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.ExecutorService

import groovy.json.JsonSlurper
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.RegHelper
import static io.seqera.wave.service.soci.SociPushEntry.Status.*
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SociPushServiceImplTest extends Specification {

    static final String AMD64 = 'sha256:1148dbb014615300fa7e713b2f94f550c0d1c69780c0805f84f093ceee34dd0b'
    static final String INDEX = 'sha256:aa943ce411423c7582288f18707ff66eddcbaeb510502d747a72aeb34f30d159'
    static final String LAYER1 = 'sha256:6fc242d221a673263eabd14a9ad9584d3177c96e5cb504c891497338e2f912f5'
    static final String LAYER3 = 'sha256:f574edac59d9f092df596f54e5b52656c803fd3ae30825b8ee5a189ed05f63d3'
    static final String GZIP = 'application/vnd.oci.image.layer.v1.tar+gzip'
    static final String TARGET = 'quay.io/user/python:3.12'
    static final String SOCI = 'quay.io/user/python:3.12-soci'

    SociConfig config = new SociConfig(spanSize: 4194304, minLayerSize: 10485760, repo: 'public.cr.stage-seqera.io/wave/soci-ztoc',
            pendingCacheDuration: Duration.ofSeconds(30), publishTimeout: Duration.ofMinutes(30), jobTimeout: Duration.ofMinutes(1))

    /** runs the async tasks in the calling thread */
    ExecutorService direct = Mock(ExecutorService) { execute(_) >> { Runnable r -> r.run() } }

    static String resource(String name) {
        SociPushServiceImplTest.getResourceAsStream("/soci/$name").text
    }

    static Map<String,ZtocEntry> ztocs() {
        return [
                (LAYER1): new ZtocEntry(LAYER1, GZIP, 'sha256:901e7c42665c70adfe9bf36bc26e4a80d006f3cc96917d43f1596fff778ffae4', 1224072, 4194304),
                (LAYER3): new ZtocEntry(LAYER3, GZIP, 'sha256:dcd11d24b6a61b29253739662ece4ab0e5af0cd39c9f9cf77a7d8f96fc5421c0', 640400, 4194304),
        ]
    }

    SociPushEntry entry(SociPushEntry.Status status) {
        SociPushEntry.of(SociPushRequest.create(TARGET, SOCI, ContainerPlatform.of('linux/amd64'), PlatformId.NULL, Files.createTempDirectory('soci')), status)
    }

    RegistryProxyService proxy() {
        Mock(RegistryProxyService) {
            getManifestContent(TARGET, PlatformId.NULL) >> resource('python-3.12-slim-index.json')
            getManifestContent("quay.io/user/python@$AMD64", PlatformId.NULL) >> resource('python-3.12-slim-amd64-manifest.json')
        }
    }

    def 'should create the soci image name' () {
        expect:
        SociPushServiceImpl.sociImage(IMAGE) == EXPECTED

        where:
        IMAGE                               | EXPECTED
        'quay.io/user/app:1.0'              | 'quay.io/user/app:1.0-soci'
        'ubuntu'                            | 'docker.io/library/ubuntu:latest-soci'
    }

    def 'should return the soci tag of a recent publication without registry requests' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def store = Mock(SociPushStateStore)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy, store: store)

        when:
        def result = service.sociTarget(TARGET, ContainerPlatform.of('linux/amd64'), PlatformId.NULL)
        then:
        1 * store.get(SOCI) >> entry(SUCCEEDED)
        0 * proxy._
        result == SOCI
    }

    def 'should return the soci tag when current on the registry' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def store = Mock(SociPushStateStore)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy, store: store)
        def target = '{"schemaVersion":2}'

        when:
        def result = service.sociTarget(TARGET, ContainerPlatform.of('linux/amd64'), PlatformId.NULL)
        then:
        1 * proxy.getManifestContent(TARGET, PlatformId.NULL) >> target
        1 * proxy.getManifestContent(SOCI, PlatformId.NULL) >> '{"annotations":{"io.seqera.wave.soci.source-digest":"' + RegHelper.digest(target) + '"}}'
        1 * store.putEntry({ SociPushEntry it -> it.succeeded() })
        result == SOCI
    }

    def 'should register the publication and wait for the container' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def store = Mock(SociPushStateStore)
        def executor = Mock(ExecutorService)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy, store: store, ioExecutor: executor)

        when:
        def result = service.sociTarget(TARGET, ContainerPlatform.of('linux/amd64'), PlatformId.NULL)
        then: 'the container does not exist yet, the build event starts the publication'
        1 * proxy.getManifestContent(TARGET, PlatformId.NULL) >> null
        1 * store.putIfAbsent(SOCI, { SociPushEntry it -> it.status == WAITING_CONTAINER }) >> true
        0 * executor.execute(_)
        result == TARGET
    }

    def 'should not publish a soci tag for a digest reference' () {
        given:
        def proxy = Mock(RegistryProxyService)
        def service = new SociPushServiceImpl(config: config, proxyService: proxy)

        expect:
        service.sociTarget('quay.io/user/app@sha256:' + 'a'*64, ContainerPlatform.of('linux/amd64'), PlatformId.NULL) == 'quay.io/user/app@sha256:' + 'a'*64
    }

    def 'should drop the publication when the build fails, without waiting' () {
        given:
        def store = Mock(SociPushStateStore)
        def index = Mock(SociIndexService)
        def service = new SociPushServiceImpl(config: config, store: store, indexService: index, ioExecutor: direct)
        def event = new BuildEvent(new BuildRequest(targetImage: TARGET), BuildResult.completed('bd-1', 1, 'error', java.time.Instant.now(), null))

        when:
        service.onBuildEvent(event)
        then:
        1 * store.get(SOCI) >> entry(WAITING_CONTAINER)
        1 * store.putEntry({ SociPushEntry it -> it.status == FAILED })
        0 * index._
    }

    def 'should start the publication on mirror completion and index the missing layers' () {
        given:
        def store = Mock(SociPushStateStore)
        def waiters = Mock(SociWaitersStore)
        def index = Mock(SociIndexService)
        def service = new SociPushServiceImpl(config: config, store: store, waiters: waiters, indexService: index, proxyService: proxy(), ioExecutor: direct)

        when:
        service.onContainerCompletion(TARGET, true)
        then:
        1 * store.get(SOCI) >> entry(WAITING_CONTAINER)
        1 * store.putEntry({ SociPushEntry it -> it.status == WAITING_INDEX })
        2 * index.findZtocs([LAYER1, LAYER3]) >> ztocs().subMap([LAYER1])
        1 * waiters.add(LAYER3, SOCI)
        1 * index.indexLayers('quay.io/user/python', { List<Map> it -> it*.digest == [LAYER3] }, PlatformId.NULL)
        0 * store.putIfAbsent(_, _, _)
    }

    def 'should push when the last ztoc completes' () {
        given:
        def store = Mock(SociPushStateStore)
        def waiters = Mock(SociWaitersStore)
        def index = Mock(SociIndexService) { findZtocs(_) >> ztocs() }
        def jobService = Mock(JobService)
        def inspect = Mock(ContainerInspectService)
        def service = new SociPushServiceImpl(config: config, store: store, waiters: waiters, indexService: index, proxyService: proxy(), jobService: jobService, inspectService: inspect, ioExecutor: direct)
        def pending = entry(WAITING_INDEX)

        when:
        service.onLayerEvent(new SociLayerEvent(LAYER3, true))
        then:
        1 * waiters.get(LAYER3) >> new SociWaiters([SOCI])
        2 * store.get(SOCI) >> pending
        1 * store.putIfAbsent("push/$SOCI", _, Duration.ofMinutes(1)) >> true
        1 * inspect.credentialsConfigJson('FROM public.cr.stage-seqera.io/wave/soci-ztoc', TARGET, null, PlatformId.NULL) >> '{}'
        1 * store.putEntry({ SociPushEntry it -> it.status == PUSHING })
        1 * jobService.launchSociPush(pending.request)
        and: 'the plan pushes the index with the source digest under the soci tag'
        def plan = new JsonSlurper().parse(pending.request.workDir.resolve('plan.json')) as Map
        plan.manifests*.reference.last() == '3.12-soci'
        def index0 = new JsonSlurper().parseText(new String((plan.manifests.last().body as String).decodeBase64())) as Map
        index0.annotations['io.seqera.wave.soci.source-digest'] == INDEX
        (index0.manifests as List<Map>).last().artifactType == 'application/vnd.amazon.soci.index.v2+json'
    }

    def 'should not push twice' () {
        given:
        def store = Mock(SociPushStateStore)
        def jobService = Mock(JobService)
        def service = new SociPushServiceImpl(config: config, store: store, jobService: jobService)

        when:
        service.push(entry(WAITING_INDEX))
        then:
        1 * store.get(SOCI) >> entry(WAITING_INDEX)
        1 * store.putIfAbsent("push/$SOCI", _, _) >> false
        0 * jobService._
    }

    def 'should fail the publication when a layer cannot be indexed' () {
        given:
        def store = Mock(SociPushStateStore)
        def waiters = Mock(SociWaitersStore)
        def service = new SociPushServiceImpl(config: config, store: store, waiters: waiters)

        when:
        service.onLayerEvent(new SociLayerEvent(LAYER3, false))
        then:
        1 * waiters.get(LAYER3) >> new SociWaiters([SOCI])
        1 * store.get(SOCI) >> entry(WAITING_INDEX)
        1 * store.putEntry({ SociPushEntry it -> it.status == FAILED })
    }
}
