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

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

class MultiPlatformBuildServiceTest extends Specification {

    def 'should create platform-specific build requests'() {
        given:
        def service = new MultiPlatformBuildService()
        def template = BuildRequest.of(
                containerId: 'abc123',
                containerFile: 'FROM ubuntu:latest',
                condaFile: null,
                workspace: Path.of('/tmp'),
                targetImage: 'docker.io/wave:abc123',
                identity: new PlatformId(new User(id: 1, email: 'foo@user.com')),
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
                identity: new PlatformId(new User(id: 1, email: 'foo@user.com')),
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
        1 * buildStore.storeIfAbsent('docker.io/wave:multi123', _)
        1 * multiBuildStore.put('docker.io/wave:multi123', _)
        1 * jobService.launchMultiBuild(_)
    }

    def 'should assemble manifest on job completion with success'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore,
                manifestAssembler: manifestAssembler
        )

        def identity = new PlatformId(new User(id: 1, email: 'foo@user.com'))
        def request = MultiBuildRequest.of(
                multiBuildId: 'mb-abc123',
                targetImage: 'docker.io/wave:multi',
                containerId: 'cid',
                buildId: 'bd-cid_0',
                amd64TargetImage: 'docker.io/wave:amd64',
                arm64TargetImage: 'docker.io/wave:arm64',
                amd64Cached: false,
                arm64Cached: false,
                identity: identity,
                creationTime: Instant.now(),
                maxDuration: Duration.ofMinutes(5)
        )
        def entry = MultiBuildEntry.of(request)
        def job = JobSpec.multiBuild('docker.io/wave:multi', 'mb-abc123', Instant.now(), Duration.ofMinutes(5))
        def state = JobState.succeeded(null)

        and:
        def existingBuildEntry = BuildEntry.create(BuildRequest.of(
                buildId: 'bd-cid_0',
                containerId: 'cid',
                targetImage: 'docker.io/wave:multi',
                startTime: Instant.now()
        ))

        when:
        service.onJobCompletion(job, entry, state)

        then:
        1 * manifestAssembler.createAndPushManifestList('docker.io/wave:multi', _, identity)
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * buildStore.getBuild('docker.io/wave:multi') >> existingBuildEntry
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
    }

    def 'should not assemble manifest on job completion with failure'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore,
                manifestAssembler: manifestAssembler
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
        def state = JobState.failed(-1, 'sub-build failed')

        and:
        def existingBuildEntry = BuildEntry.create(BuildRequest.of(
                buildId: 'bd-cid_0',
                containerId: 'cid',
                targetImage: 'docker.io/wave:multi',
                startTime: Instant.now()
        ))

        when:
        service.onJobCompletion(job, entry, state)

        then:
        0 * manifestAssembler.createAndPushManifestList(_, _, _)
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * buildStore.getBuild('docker.io/wave:multi') >> existingBuildEntry
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
    }

    def 'should handle job timeout'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore
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

        and:
        def existingBuildEntry = BuildEntry.create(BuildRequest.of(
                buildId: 'bd-cid_0',
                containerId: 'cid',
                targetImage: 'docker.io/wave:multi',
                startTime: Instant.now()
        ))

        when:
        service.onJobTimeout(job, entry)

        then:
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * buildStore.getBuild('docker.io/wave:multi') >> existingBuildEntry
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
    }

    def 'should handle job exception'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def multiBuildStore = Mock(MultiBuildStateStore)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                multiBuildStore: multiBuildStore
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

        and:
        def existingBuildEntry = BuildEntry.create(BuildRequest.of(
                buildId: 'bd-cid_0',
                containerId: 'cid',
                targetImage: 'docker.io/wave:multi',
                startTime: Instant.now()
        ))

        when:
        service.onJobException(job, entry, new RuntimeException('boom'))

        then:
        1 * multiBuildStore.put('docker.io/wave:multi', _)
        1 * buildStore.getBuild('docker.io/wave:multi') >> existingBuildEntry
        1 * buildStore.storeBuild('docker.io/wave:multi', _)
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
