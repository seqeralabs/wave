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

import groovy.json.JsonOutput
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.soci.strategy.SociIndexStrategy
import io.seqera.wave.tower.PlatformId
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SociIndexServiceImplTest extends Specification {

    static final String SMALL = 'sha256:' + '1'*64
    static final String CACHED = 'sha256:' + '2'*64
    static final String BIG = 'sha256:' + '3'*64
    static final String GZIP = 'application/vnd.oci.image.layer.v1.tar+gzip'

    SociConfig config = new SociConfig(spanSize: 4194304, minLayerSize: 10485760, repo: 'registry.io/wave/soci-ztoc',
            pendingCacheDuration: Duration.ofSeconds(30), jobTimeout: Duration.ofMinutes(10))

    static String manifest() {
        JsonOutput.toJson([
                schemaVersion: 2,
                mediaType: 'application/vnd.oci.image.manifest.v1+json',
                config: [mediaType: 'application/vnd.oci.image.config.v1+json', digest: 'sha256:c0', size: 10],
                layers: [
                        [mediaType: GZIP, digest: SMALL, size: 1048576],
                        [mediaType: GZIP, digest: CACHED, size: 20000000],
                        [mediaType: GZIP, digest: BIG, size: 52428800] ] ])
    }

    static ZtocEntry ztoc(String layer) {
        new ZtocEntry(layer, GZIP, 'sha256:z' + layer.substring(8, 12), 1000, 4194304)
    }

    def 'should launch one job per missing eligible layer' () {
        given:
        def ztocStore = Mock(SociZtocStore)
        def jobStore = Mock(SociIndexStateStore)
        def jobService = Mock(JobService)
        def proxy = Mock(RegistryProxyService)
        def inspect = Mock(ContainerInspectService)
        def service = new SociIndexServiceImpl(config: config, ztocStore: ztocStore, jobStore: jobStore, jobService: jobService, proxyService: proxy, inspectService: inspect)

        when:
        service.indexLayers('docker.io/library/app', manifest(), PlatformId.NULL)

        then:
        ztocStore.get("4194304/$CACHED") >> ztoc(CACHED)
        ztocStore.get("4194304/$BIG") >> null
        jobStore.get("4194304/$BIG") >> null
        1 * proxy.getManifestContent("registry.io/wave/soci-ztoc:ztoc-4194304-${'3'*64}", null) >> null
        1 * inspect.credentialsConfigJson('FROM docker.io/library/app', 'registry.io/wave/soci-ztoc', null, PlatformId.NULL) >> '{"auths":{}}'
        1 * jobStore.putIfAbsent("4194304/$BIG", _ as SociIndexEntry) >> true
        1 * jobService.launchSociIndex({ SociIndexRequest it ->
            it.source == "docker.io/library/app@$BIG" &&
            it.layerDigest == BIG &&
            it.layerSize == 52428800 &&
            it.repository == 'registry.io/wave/soci-ztoc' &&
            it.authJson == '{"auths":{}}' &&
            it.requestId.startsWith('soci-') })
        0 * jobService._
    }

    def 'should dedup concurrent requests for the same layer' () {
        given:
        def ztocStore = Mock(SociZtocStore)
        def jobStore = Mock(SociIndexStateStore)
        def jobService = Mock(JobService)
        def proxy = Mock(RegistryProxyService)
        def service = new SociIndexServiceImpl(config: config, ztocStore: ztocStore, jobStore: jobStore, jobService: jobService, proxyService: proxy, inspectService: Mock(ContainerInspectService))

        when:
        service.indexLayers('docker.io/library/app', manifest(), PlatformId.NULL)

        then:
        ztocStore.get(_) >> null
        jobStore.get(_) >> null
        proxy.getManifestContent(_, null) >> null
        2 * jobStore.putIfAbsent(_, _) >> false
        0 * jobService.launchSociIndex(_)
    }

    def 'should restore entry from registry tag annotations when Redis misses' () {
        given:
        def ztocStore = Mock(SociZtocStore)
        def jobStore = Mock(SociIndexStateStore)
        def proxy = Mock(RegistryProxyService)
        def service = new SociIndexServiceImpl(config: config, ztocStore: ztocStore, jobStore: jobStore, proxyService: proxy)
        def artifact = JsonOutput.toJson([
                schemaVersion: 2,
                mediaType: 'application/vnd.oci.image.manifest.v1+json',
                artifactType: 'application/vnd.seqera.wave.ztoc.v1+json',
                annotations: [
                        'io.seqera.wave.ztoc.layer-digest': BIG,
                        'io.seqera.wave.ztoc.layer-mediaType': GZIP,
                        'io.seqera.wave.ztoc.digest': 'sha256:zbig',
                        'io.seqera.wave.ztoc.size': '1234',
                        'io.seqera.wave.ztoc.span-size': '4194304' ] ])

        when:
        def result = service.findZtocs([BIG, CACHED])

        then:
        ztocStore.get(_) >> null
        jobStore.get(_) >> null
        1 * proxy.getManifestContent("registry.io/wave/soci-ztoc:ztoc-4194304-${'3'*64}", null) >> artifact
        1 * proxy.getManifestContent("registry.io/wave/soci-ztoc:ztoc-4194304-${'2'*64}", null) >> null
        1 * ztocStore.put("4194304/$BIG", new ZtocEntry(BIG, GZIP, 'sha256:zbig', 1234, 4194304))
        and: 'a missing tag means not indexed'
        result == [(BIG): new ZtocEntry(BIG, GZIP, 'sha256:zbig', 1234, 4194304)]
    }

    def 'should not look up the repository while a job is running' () {
        given:
        def ztocStore = Mock(SociZtocStore)
        def jobStore = Mock(SociIndexStateStore)
        def proxy = Mock(RegistryProxyService)
        def service = new SociIndexServiceImpl(config: config, ztocStore: ztocStore, jobStore: jobStore, proxyService: proxy)

        when:
        def result = service.findZtocs([BIG])
        then:
        ztocStore.get(_) >> null
        jobStore.get("4194304/$BIG") >> Mock(SociIndexEntry)
        0 * proxy.getManifestContent(_, _)
        result == [:]
    }

    def 'should store entry on job completion' () {
        given:
        def ztocStore = Mock(SociZtocStore)
        def jobStore = Mock(SociIndexStateStore)
        def service = new SociIndexServiceImpl(config: config, ztocStore: ztocStore, jobStore: jobStore)
        def request = new SociIndexRequest(requestId: 'soci-1', layerDigest: BIG, spanSize: 4194304)
        def entry = SociIndexEntry.of(request)
        def stdout = 'some log line\n{"layerDigest":"' + BIG + '","layerMediaType":"' + GZIP + '","ztocDigest":"sha256:zbig","ztocSize":1234,"spanSize":4194304,"skipped":false}\n'

        when:
        service.onJobCompletion(Mock(JobSpec), entry, JobState.completed(0, stdout))
        then:
        1 * ztocStore.put("4194304/$BIG", new ZtocEntry(BIG, GZIP, 'sha256:zbig', 1234, 4194304))
        1 * jobStore.putEntry({ SociIndexEntry it -> it.done() && it.succeeded() && it.ztoc.ztocDigest == 'sha256:zbig' })
    }

    def 'should not store entry on job failure and allow retry' () {
        given:
        def ztocStore = Mock(SociZtocStore)
        def jobStore = Mock(SociIndexStateStore)
        def service = new SociIndexServiceImpl(config: config, ztocStore: ztocStore, jobStore: jobStore)
        def entry = SociIndexEntry.of(new SociIndexRequest(requestId: 'soci-1', layerDigest: BIG, spanSize: 4194304))

        when:
        service.onJobCompletion(Mock(JobSpec), entry, JobState.completed(1, 'soci-indexer: unable to access layer'))
        then:
        0 * ztocStore.put(_, _)
        1 * jobStore.putEntry({ SociIndexEntry it -> it.done() && !it.succeeded() && it.error.contains('unable to access layer') })
    }

    def 'should launch the job with the docker auth' () {
        given:
        def strategy = Mock(SociIndexStrategy)
        def service = new SociIndexServiceImpl(config: config, strategy: strategy)
        def dir = java.nio.file.Files.createTempDirectory('test')
        def request = new SociIndexRequest(requestId: 'soci-1', layerDigest: BIG, spanSize: 4194304, workDir: dir.resolve('soci-1'), authJson: '{"auths":{}}')
        def job = JobSpec.sociIndex(request.key, 'soci-1', java.time.Instant.now(), Duration.ofMinutes(1), request.workDir)

        when:
        def result = service.launchJob(job, SociIndexEntry.of(request))
        then:
        1 * strategy.launchJob('soci-1', request)
        result.launchTime != null
        java.nio.file.Files.exists(dir.resolve('soci-1/config.json'))

        cleanup:
        dir?.deleteDir()
    }

    def 'should parse the result line' () {
        expect:
        SociIndexServiceImpl.parseResult(STDOUT)?.ztocDigest == EXPECTED

        where:
        STDOUT                                                                  | EXPECTED
        null                                                                    | null
        'error: something'                                                      | null
        '{"layerDigest":"sha256:a","ztocDigest":"sha256:z"}'                    | 'sha256:z'
        'log\n{"layerDigest":"sha256:a","ztocDigest":"sha256:z"}\n'             | 'sha256:z'
        '{"layerDigest":"sha256:a","skipped":true,"reason":"min-size"}'         | null
    }
}
