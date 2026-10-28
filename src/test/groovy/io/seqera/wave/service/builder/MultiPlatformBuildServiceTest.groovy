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

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.context.event.ApplicationEventPublisher
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

class MultiPlatformBuildServiceTest extends Specification {

    static final PlatformId TEST_IDENTITY = new PlatformId(new User(id: 1, email: 'foo@user.com'))

    private BuildEntry makeExistingBuildEntry() {
        BuildEntry.create(BuildRequest.of(
                buildId: 'bd-cid_0',
                containerId: 'cid',
                targetImage: 'docker.io/wave:multi',
                startTime: Instant.now(),
                identity: TEST_IDENTITY,
                platform: ContainerPlatform.MULTI_PLATFORM
        ))
    }

    def 'should create platform-specific build requests with noEmail flag'() {
        given:
        def service = new MultiPlatformBuildService()
        def template = BuildRequest.of(
                containerId: 'abc123',
                containerFile: 'FROM ubuntu:latest',
                condaFile: null,
                workspace: Path.of('/tmp'),
                targetImage: 'docker.io/wave:abc123',
                identity: TEST_IDENTITY,
                platform: ContainerPlatform.of('linux/amd64'),
                cacheRepository: 'docker.io/cache',
                ip: '10.0.0.1',
                configJson: '{}',
                offsetId: '+0',
                format: BuildFormat.DOCKER,
                maxDuration: Duration.ofMinutes(5),
                buildTemplate: null
        )

        when:
        def amd64Req = service.createPlatformRequest(template, MultiPlatformBuildService.PLATFORM_AMD64, '-linux-amd64')
        def arm64Req = service.createPlatformRequest(template, MultiPlatformBuildService.PLATFORM_ARM64, '-linux-arm64')

        then:
        amd64Req.platform == ContainerPlatform.of('linux/amd64')
        arm64Req.platform == ContainerPlatform.of('linux/arm64')
        amd64Req.containerFile == 'FROM ubuntu:latest'
        arm64Req.containerFile == 'FROM ubuntu:latest'
        amd64Req.containerId != arm64Req.containerId
        amd64Req.targetImage != arm64Req.targetImage
        amd64Req.cacheRepository == 'docker.io/cache'
        arm64Req.cacheRepository == 'docker.io/cache'
        and: 'sub-builds should have noEmail flag set'
        amd64Req.noEmail == true
        arm64Req.noEmail == true
        and: 'sub-builds should not trigger individual scans'
        amd64Req.scanId == null
        arm64Req.scanId == null
    }

    def 'should create platform-specific build requests for singularity format'() {
        given:
        def service = new MultiPlatformBuildService()
        def template = BuildRequest.of(
                containerId: 'abc123',
                containerFile: 'Bootstrap: docker\nFrom: ubuntu:latest',
                workspace: Path.of('/tmp'),
                targetImage: 'oras://docker.io/foo/wave:abc123',
                identity: TEST_IDENTITY,
                platform: ContainerPlatform.MULTI_PLATFORM,
                ip: '10.0.0.1',
                configJson: '{}',
                offsetId: '+0',
                format: BuildFormat.SINGULARITY,
                maxDuration: Duration.ofMinutes(5)
        )

        when:
        def amd64Req = service.createPlatformRequest(template, MultiPlatformBuildService.PLATFORM_AMD64, '-linux-amd64')
        def arm64Req = service.createPlatformRequest(template, MultiPlatformBuildService.PLATFORM_ARM64, '-linux-arm64')

        then: 'both sub-builds keep the singularity format'
        amd64Req.format == BuildFormat.SINGULARITY
        arm64Req.format == BuildFormat.SINGULARITY

        and: 'the per-platform target images use the oras:// scheme'
        amd64Req.targetImage.startsWith('oras://docker.io/foo/wave:')
        arm64Req.targetImage.startsWith('oras://docker.io/foo/wave:')
        amd64Req.targetImage != arm64Req.targetImage

        and: 'each sub-build targets its own platform'
        amd64Req.platform == ContainerPlatform.of('linux/amd64')
        arm64Req.platform == ContainerPlatform.of('linux/arm64')

        and: 'SIF images are not scanned'
        amd64Req.scanId == null
        arm64Req.scanId == null
    }

    def 'should return in-progress build track and launch multi-build job'() {
        given:
        def buildService = Mock(ContainerBuildService)
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)
        def jobService = Mock(JobService)

        def service = new MultiPlatformBuildService(
                buildService: buildService,
                buildStore: buildStore,
                multiBuildStore: multiBuildStore,
                manifestAssembler: manifestAssembler,
                jobService: jobService
        )

        def template = BuildRequest.of(
                containerId: 'abc123',
                containerFile: 'FROM ubuntu:latest',
                workspace: Path.of('/tmp'),
                targetImage: 'docker.io/wave:abc123',
                identity: TEST_IDENTITY,
                platform: ContainerPlatform.of('linux/amd64'),
                format: BuildFormat.DOCKER,
                maxDuration: Duration.ofMinutes(5)
        )

        and:
        buildService.buildImage(_) >> new BuildTrack('bd-amd64_0', 'docker.io/wave:amd64', false, null) >> new BuildTrack('bd-arm64_0', 'docker.io/wave:arm64', false, null)

        when:
        def track = service.buildMultiPlatformImage(template, 'multi123', 'docker.io/wave:multi123', PlatformId.NULL)

        then:
        track.targetImage == 'docker.io/wave:multi123'
        track.cached == false
        track.succeeded == null
        track.id == 'bd-multi123_0'
        and:
        1 * buildStore.storeIfAbsent('docker.io/wave:multi123', _) >> true
        1 * multiBuildStore.put('docker.io/wave:multi123', _)
        1 * jobService.launchMultiBuild(_)
    }

    def 'should assemble manifest and persist record on job completion with success'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)
        def eventPublisher = Mock(ApplicationEventPublisher)
        def persistenceService = Mock(PersistenceService)
        def scanService = Mock(ContainerScanService)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore,
                manifestAssembler: manifestAssembler,
                eventPublisher: eventPublisher,
                persistenceService: persistenceService,
                scanService: scanService
        )

        def request = MultiBuildRequest.of(
                multiBuildId: 'mb-abc123',
                targetImage: 'docker.io/wave:multi',
                containerId: 'cid',
                buildId: 'bd-cid_0',
                amd64TargetImage: 'docker.io/wave:amd64',
                arm64TargetImage: 'docker.io/wave:arm64',
                amd64Cached: false,
                arm64Cached: false,
                identity: TEST_IDENTITY,
                creationTime: Instant.now(),
                maxDuration: Duration.ofMinutes(5)
        )
        def entry = MultiBuildEntry.of(request)
        def job = JobSpec.multiBuild('docker.io/wave:multi', 'mb-abc123', Instant.now(), Duration.ofMinutes(5))
        def state = JobState.succeeded(null)

        when:
        service.onJobCompletion(job, entry, state)

        then:
        1 * manifestAssembler.createAndPushManifestList('docker.io/wave:multi', _, TEST_IDENTITY)
        and:
        1 * buildStore.getBuild('docker.io/wave:multi') >> makeExistingBuildEntry()
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * scanService.scanOnBuild({ BuildEntry e -> e.request.targetImage == 'docker.io/wave:multi' })
        1 * persistenceService.saveBuildAsync({ WaveBuildRecord r -> r.platform == 'linux/amd64,linux/arm64' })
        1 * eventPublisher.publishEvent({ BuildEvent e -> e.request.platform?.isMultiArch() })
    }

    def 'should persist failure record when sub-build fails'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)
        def eventPublisher = Mock(ApplicationEventPublisher)
        def persistenceService = Mock(PersistenceService)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore,
                manifestAssembler: manifestAssembler,
                eventPublisher: eventPublisher,
                persistenceService: persistenceService
        )

        def request = MultiBuildRequest.of(
                multiBuildId: 'mb-abc123',
                targetImage: 'docker.io/wave:multi',
                containerId: 'cid',
                buildId: 'bd-cid_0',
                amd64TargetImage: 'docker.io/wave:amd64',
                arm64TargetImage: 'docker.io/wave:arm64',
                amd64Cached: false,
                arm64Cached: false,
                identity: TEST_IDENTITY,
                creationTime: Instant.now(),
                maxDuration: Duration.ofMinutes(5)
        )
        def entry = MultiBuildEntry.of(request)
        def job = JobSpec.multiBuild('docker.io/wave:multi', 'mb-abc123', Instant.now(), Duration.ofMinutes(5))
        def state = JobState.failed(-1, 'sub-build failed')

        when:
        service.onJobCompletion(job, entry, state)

        then:
        0 * manifestAssembler.createAndPushManifestList(_, _, _)
        and:
        1 * buildStore.getBuild('docker.io/wave:multi') >> makeExistingBuildEntry()
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * persistenceService.saveBuildAsync(_)
        1 * eventPublisher.publishEvent(_ as BuildEvent)
    }

    def 'should handle job timeout'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)
        def eventPublisher = Mock(ApplicationEventPublisher)
        def persistenceService = Mock(PersistenceService)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore,
                eventPublisher: eventPublisher,
                persistenceService: persistenceService
        )

        def request = MultiBuildRequest.of(
                multiBuildId: 'mb-abc123',
                targetImage: 'docker.io/wave:multi',
                containerId: 'cid',
                buildId: 'bd-cid_0',
                amd64TargetImage: 'docker.io/wave:amd64',
                arm64TargetImage: 'docker.io/wave:arm64',
                amd64Cached: false,
                arm64Cached: false,
                creationTime: Instant.now(),
                maxDuration: Duration.ofMinutes(5)
        )
        def entry = MultiBuildEntry.of(request)
        def job = JobSpec.multiBuild('docker.io/wave:multi', 'mb-abc123', Instant.now(), Duration.ofMinutes(5))

        when:
        service.onJobTimeout(job, entry)

        then:
        1 * buildStore.getBuild('docker.io/wave:multi') >> makeExistingBuildEntry()
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * persistenceService.saveBuildAsync(_)
        1 * eventPublisher.publishEvent(_ as BuildEvent)
    }

    def 'should handle job exception'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)
        def eventPublisher = Mock(ApplicationEventPublisher)
        def persistenceService = Mock(PersistenceService)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore,
                eventPublisher: eventPublisher,
                persistenceService: persistenceService
        )

        def request = MultiBuildRequest.of(
                multiBuildId: 'mb-abc123',
                targetImage: 'docker.io/wave:multi',
                containerId: 'cid',
                buildId: 'bd-cid_0',
                amd64TargetImage: 'docker.io/wave:amd64',
                arm64TargetImage: 'docker.io/wave:arm64',
                amd64Cached: false,
                arm64Cached: false,
                creationTime: Instant.now(),
                maxDuration: Duration.ofMinutes(5)
        )
        def entry = MultiBuildEntry.of(request)
        def job = JobSpec.multiBuild('docker.io/wave:multi', 'mb-abc123', Instant.now(), Duration.ofMinutes(5))

        when:
        service.onJobException(job, entry, new RuntimeException('boom'))

        then:
        1 * buildStore.getBuild('docker.io/wave:multi') >> makeExistingBuildEntry()
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * persistenceService.saveBuildAsync(_)
        1 * eventPublisher.publishEvent(_ as BuildEvent)
    }

    def 'should filter fusion layers for target platform'() {
        given:
        def amd64Layer = new ContainerLayer(
                location: 'https://fusionfs.seqera.io/releases/pkg/2/4/20/fusion-amd64.tar.gz',
                gzipDigest: 'sha256:aaa',
                gzipSize: 100,
                tarDigest: 'sha256:bbb'
        )
        def arm64Layer = new ContainerLayer(
                location: 'https://fusionfs.seqera.io/releases/pkg/2/4/20/fusion-arm64.tar.gz',
                gzipDigest: 'sha256:ccc',
                gzipSize: 200,
                tarDigest: 'sha256:ddd'
        )
        def amd64SnapLayer = new ContainerLayer(
                location: 'https://fusionfs.seqera.io/fusion-snapshot/releases/1/9/0/fusion-snapshot-amd64.tgz',
                gzipDigest: 'sha256:ggg',
                gzipSize: 300,
                tarDigest: 'sha256:hhh'
        )
        def arm64SnapLayer = new ContainerLayer(
                location: 'https://fusionfs.seqera.io/fusion-snapshot/releases/1/9/0/fusion-snapshot-arm64.tgz',
                gzipDigest: 'sha256:iii',
                gzipSize: 400,
                tarDigest: 'sha256:jjj'
        )
        def dataLayer = new ContainerLayer(
                location: 'data:H4sIAAAAAAAA/+2STQ6C',
                gzipDigest: 'sha256:eee',
                gzipSize: 50,
                tarDigest: 'sha256:fff'
        )
        def config = new ContainerConfig(
                ['/usr/bin/fusion'] as List<String>,
                null,
                ['FUSION_CONFIG_PROFILE=nextflow'] as List<String>,
                null,
                [dataLayer, amd64Layer, amd64SnapLayer, arm64Layer, arm64SnapLayer]
        )

        when: 'filtering for amd64'
        def amd64Config = MultiPlatformBuildService.filterLayersForPlatform(config, ContainerPlatform.of('linux/amd64'))
        then:
        amd64Config.layers*.location == [dataLayer, amd64Layer, amd64SnapLayer]*.location
        amd64Config.entrypoint == ['/usr/bin/fusion']
        amd64Config.env == ['FUSION_CONFIG_PROFILE=nextflow']

        when: 'filtering for arm64'
        def arm64Config = MultiPlatformBuildService.filterLayersForPlatform(config, ContainerPlatform.of('linux/arm64'))
        then:
        arm64Config.layers*.location == [dataLayer, arm64Layer, arm64SnapLayer]*.location
    }

    def 'should remove the other platform layers from a frozen container file'() {
        given:
        def amd64Layer = new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/pkg/2/6/10/fusion-amd64.tar.gz', gzipDigest: 'sha256:aaa')
        def arm64Layer = new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/pkg/2/6/10/fusion-arm64.tar.gz', gzipDigest: 'sha256:bbb')
        def amd64SnapLayer = new ContainerLayer(location: 'https://fusionfs.seqera.io/fusion-snapshot/releases/1/9/0/fusion-snapshot-amd64.tgz', gzipDigest: 'sha256:ccc')
        def arm64SnapLayer = new ContainerLayer(location: 'https://fusionfs.seqera.io/fusion-snapshot/releases/1/9/0/fusion-snapshot-arm64.tgz', gzipDigest: 'sha256:ddd')
        def config = new ContainerConfig(['/usr/bin/fusion'], null, null, null, [amd64Layer, amd64SnapLayer, arm64Layer, arm64SnapLayer])
        def amd64Config = MultiPlatformBuildService.filterLayersForPlatform(config, ContainerPlatform.of('linux/amd64'))
        def arm64Config = MultiPlatformBuildService.filterLayersForPlatform(config, ContainerPlatform.of('linux/arm64'))

        when: 'docker format'
        def dockerFile = 'FROM ubuntu:latest\n' + FreezeServiceImpl.appendDockerConfig0(config)
        then:
        MultiPlatformBuildService.removeDroppedLayers(dockerFile, config, amd64Config) == """\
            FROM ubuntu:latest
            ADD layer-aaa.tar.gz /
            ADD layer-ccc.tar.gz /
            ENTRYPOINT ["/usr/bin/fusion"]
            """.stripIndent()
        MultiPlatformBuildService.removeDroppedLayers(dockerFile, config, arm64Config) == """\
            FROM ubuntu:latest
            ADD layer-bbb.tar.gz /
            ADD layer-ddd.tar.gz /
            ENTRYPOINT ["/usr/bin/fusion"]
            """.stripIndent()

        when: 'singularity format'
        def singularityFile = 'BootStrap: docker\nFrom: ubuntu:latest\n' + FreezeServiceImpl.appendSingularityConfig0(config)
        def amd64File = MultiPlatformBuildService.removeDroppedLayers(singularityFile, config, amd64Config)
        then:
        amd64File.contains('{{wave_context_dir}}/layer-aaa/* /')
        amd64File.contains('{{wave_context_dir}}/layer-ccc/* /')
        !amd64File.contains('layer-bbb')
        !amd64File.contains('layer-ddd')

        and: 'container file unchanged when no layer is dropped'
        MultiPlatformBuildService.removeDroppedLayers('FROM ubuntu:latest\n', config, config) == 'FROM ubuntu:latest\n'
        MultiPlatformBuildService.removeDroppedLayers('FROM ubuntu:latest', null, null) == 'FROM ubuntu:latest'

        and: 'line terminators are preserved'
        MultiPlatformBuildService.removeDroppedLayers('FROM ubuntu:latest\r\nADD layer-aaa.tar.gz /\r\nADD layer-bbb.tar.gz /\r\n', config, amd64Config) == 'FROM ubuntu:latest\r\nADD layer-aaa.tar.gz /\r\n'
        MultiPlatformBuildService.removeDroppedLayers('FROM ubuntu:latest\nADD layer-bbb.tar.gz /', config, amd64Config) == 'FROM ubuntu:latest\n'
    }

    def 'should create platform requests with only the platform layers in a frozen container file'() {
        given:
        def service = new MultiPlatformBuildService()
        def amd64Layer = new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/pkg/2/6/10/fusion-amd64.tar.gz', gzipDigest: 'sha256:aaa')
        def arm64Layer = new ContainerLayer(location: 'https://fusionfs.seqera.io/releases/pkg/2/6/10/fusion-arm64.tar.gz', gzipDigest: 'sha256:bbb')
        def config = new ContainerConfig(['/usr/bin/fusion'], null, null, null, [amd64Layer, arm64Layer])
        def template = BuildRequest.of(
                containerId: 'abc123',
                containerFile: 'FROM ubuntu:latest\n' + FreezeServiceImpl.appendDockerConfig0(config),
                workspace: Path.of('/tmp'),
                targetImage: 'docker.io/wave:abc123',
                identity: TEST_IDENTITY,
                platform: ContainerPlatform.MULTI_PLATFORM,
                containerConfig: config,
                ip: '10.0.0.1',
                configJson: '{}',
                offsetId: '+0',
                format: BuildFormat.DOCKER,
                maxDuration: Duration.ofMinutes(5)
        )

        when:
        def amd64Req = service.createPlatformRequest(template, MultiPlatformBuildService.PLATFORM_AMD64, '-linux-amd64')
        def arm64Req = service.createPlatformRequest(template, MultiPlatformBuildService.PLATFORM_ARM64, '-linux-arm64')

        then:
        amd64Req.containerConfig.layers == [amd64Layer]
        amd64Req.containerFile == 'FROM ubuntu:latest\nADD layer-aaa.tar.gz /\nENTRYPOINT ["/usr/bin/fusion"]\n'
        and:
        arm64Req.containerConfig.layers == [arm64Layer]
        arm64Req.containerFile == 'FROM ubuntu:latest\nADD layer-bbb.tar.gz /\nENTRYPOINT ["/usr/bin/fusion"]\n'
    }

    def 'should return config as-is when no fusion layers'() {
        given:
        def dataLayer = new ContainerLayer(location: 'data:H4sIAAAAAAAA/+2STQ6C')
        def config = new ContainerConfig(null, null, null, null, [dataLayer])

        when:
        def result = MultiPlatformBuildService.filterLayersForPlatform(config, ContainerPlatform.of('linux/amd64'))

        then:
        result.layers.size() == 1
        result.layers[0].location == dataLayer.location
    }

    def 'should handle null and empty config'() {
        expect:
        MultiPlatformBuildService.filterLayersForPlatform(null, ContainerPlatform.of('linux/amd64')) == null
        MultiPlatformBuildService.filterLayersForPlatform(new ContainerConfig(), ContainerPlatform.of('linux/amd64')).layers == []
    }

    def 'launchJob should be a no-op returning job with launch time'() {
        given:
        def service = new MultiPlatformBuildService()
        def job = JobSpec.multiBuild('docker.io/wave:multi', 'mb-abc123', Instant.now(), Duration.ofMinutes(5))
        def entry = Mock(MultiBuildEntry)

        when:
        def result = service.launchJob(job, entry)

        then:
        result.id == job.id
        result.type == job.type
        result.launchTime != null
    }
}
