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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

import io.seqera.wave.core.ContainerPlatform
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

    def 'should return in-progress build track'() {
        given:
        def buildService = Mock(ContainerBuildService)
        def buildStore = Mock(BuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)

        def service = new MultiPlatformBuildService(
                buildService: buildService,
                buildStore: buildStore,
                manifestAssembler: manifestAssembler,
                executor: Executors.newSingleThreadExecutor()
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
    }

    def 'should assemble manifest list on both builds succeeding'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                manifestAssembler: manifestAssembler,
                executor: Executors.newSingleThreadExecutor()
        )

        def amd64Track = new BuildTrack('bd-amd64_0', 'docker.io/wave:amd64', false, null)
        def arm64Track = new BuildTrack('bd-arm64_0', 'docker.io/wave:arm64', false, null)
        def identity = PlatformId.NULL

        and:
        def now = Instant.now()
        def amd64Result = BuildResult.completed('bd-amd64_0', 0, 'ok', now, 'sha256:aaa')
        def arm64Result = BuildResult.completed('bd-arm64_0', 0, 'ok', now, 'sha256:bbb')
        buildStore.awaitBuild('docker.io/wave:amd64') >> CompletableFuture.completedFuture(amd64Result)
        buildStore.awaitBuild('docker.io/wave:arm64') >> CompletableFuture.completedFuture(arm64Result)

        when:
        service.awaitAndAssemble(amd64Track, arm64Track, 'docker.io/wave:final', identity)

        then:
        1 * manifestAssembler.createAndPushManifestList('docker.io/wave:final', ['docker.io/wave:amd64', 'docker.io/wave:arm64'], identity)
    }

    def 'should not assemble manifest list when a build fails'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                manifestAssembler: manifestAssembler,
                executor: Executors.newSingleThreadExecutor()
        )

        def amd64Track = new BuildTrack('bd-amd64_0', 'docker.io/wave:amd64', false, null)
        def arm64Track = new BuildTrack('bd-arm64_0', 'docker.io/wave:arm64', false, null)
        def identity = PlatformId.NULL

        and:
        def now = Instant.now()
        def amd64Result = BuildResult.completed('bd-amd64_0', 0, 'ok', now, 'sha256:aaa')
        def arm64Result = BuildResult.failed('bd-arm64_0', 'error', now)
        buildStore.awaitBuild('docker.io/wave:amd64') >> CompletableFuture.completedFuture(amd64Result)
        buildStore.awaitBuild('docker.io/wave:arm64') >> CompletableFuture.completedFuture(arm64Result)

        when:
        service.awaitAndAssemble(amd64Track, arm64Track, 'docker.io/wave:final', identity)

        then:
        0 * manifestAssembler.createAndPushManifestList(_, _, _)
    }

    def 'should handle cached platform builds'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def manifestAssembler = Mock(ManifestAssembler)

        def service = new MultiPlatformBuildService(
                buildStore: buildStore,
                manifestAssembler: manifestAssembler,
                executor: Executors.newSingleThreadExecutor()
        )

        def amd64Track = new BuildTrack('bd-amd64_0', 'docker.io/wave:amd64', true, true)
        def arm64Track = new BuildTrack('bd-arm64_0', 'docker.io/wave:arm64', true, true)
        def identity = PlatformId.NULL

        when:
        service.awaitAndAssemble(amd64Track, arm64Track, 'docker.io/wave:final', identity)

        then:
        0 * buildStore.awaitBuild(_)
        1 * manifestAssembler.createAndPushManifestList('docker.io/wave:final', ['docker.io/wave:amd64', 'docker.io/wave:arm64'], identity)
    }
}
