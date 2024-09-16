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

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

import groovy.util.logging.Slf4j
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.store.BuildRecordStore
import io.seqera.wave.service.inspect.ContainerInspectServiceImpl
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.test.TestHelper
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.ContainerHelper
import io.seqera.wave.util.Packer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MicronautTest
class ContainerBuildServiceLiveTest extends Specification {

    @Inject ContainerBuildServiceImpl service
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject ContainerInspectServiceImpl dockerAuthService
    @Inject HttpClientConfig httpClientConfig
    @Inject BuildConfig buildConfig
    @Inject BuildRecordStore buildRecordStore
    @Inject BuildCacheStore buildCacheStore
    @Inject PersistenceService persistenceService
    @Inject JobService jobService

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should build & push container to aws' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = buildConfig.defaultBuildRepository
        def cacheRepo = buildConfig.defaultCacheRepository
        def duration = Duration.ofMinutes(1)
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, cacheRepo, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: ContainerPlatform.of('amd64'),
                        cacheRepository: cacheRepo,
                        configJson: cfg,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        maxDuration: duration
                )
                    .withBuildId('1')
        and:
        buildCacheStore.storeBuild(targetImage, new BuildStoreEntry(req, BuildResult.create(req)))

        when:
        service.launch(req)
        then:
        service
                .buildResult(targetImage)
                .get(duration.toSeconds(), TimeUnit.SECONDS)
                .succeeded()

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('DOCKER_USER') && System.getenv('DOCKER_PAT')})
    def 'should build & push container to docker.io' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = "docker.io/pditommaso/wave-tests"
        def cacheRepo = buildConfig.defaultCacheRepository
        def duration = Duration.ofMinutes(1)
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: TestHelper.containerPlatform(),
                        cacheRepository: cacheRepo,
                        configJson: cfg,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        maxDuration: duration
                )
                .withBuildId('1')
        and:
        buildCacheStore.storeBuild(targetImage, new BuildStoreEntry(req, BuildResult.create(req)))

        when:
        service.launch(req)
        then:
        service
                .buildResult(targetImage)
                .get(duration.toSeconds(), TimeUnit.SECONDS)
                .succeeded()

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('QUAY_USER') && System.getenv('QUAY_PAT')})
    def 'should build & push container to quay.io' () {
        given:
        def folder = Files.createTempDirectory('test')
        def cacheRepo = buildConfig.defaultCacheRepository
        def duration = Duration.ofMinutes(1)
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def buildRepo = "quay.io/pditommaso/wave-tests"
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('linux/arm64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: TestHelper.containerPlatform(),
                        cacheRepository: cacheRepo,
                        configJson: cfg,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        maxDuration: duration
                )
                .withBuildId('1')
        and:
        buildCacheStore.storeBuild(targetImage, new BuildStoreEntry(req, BuildResult.create(req)))

        when:
        service.launch(req)
        then:
        service
                .buildResult(targetImage)
                .get(duration.toSeconds(), TimeUnit.SECONDS)
                .succeeded()

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('AZURECR_USER') && System.getenv('AZURECR_PAT')})
    def 'should build & push container to azure' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = "seqeralabs.azurecr.io/wave-tests"
        def cacheRepo = buildConfig.defaultCacheRepository
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def duration = Duration.ofMinutes(1)
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: TestHelper.containerPlatform(),
                        cacheRepository: cacheRepo,
                        configJson: cfg,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        maxDuration: duration
                )
                .withBuildId('1')
        and:
        buildCacheStore.storeBuild(targetImage, new BuildStoreEntry(req, BuildResult.create(req)))

        when:
        service.launch(req)
        then:
        service
                .buildResult(targetImage)
                .get(duration.toSeconds(), TimeUnit.SECONDS)
                .succeeded()

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('DOCKER_USER') && System.getenv('DOCKER_PAT')})
    def 'should build & push container to docker.io with local layers' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = "docker.io/pditommaso/wave-tests"
        def cacheRepo = buildConfig.defaultCacheRepository
        def layer = Files.createDirectories(folder.resolve('layer'))
        def file1 = layer.resolve('hola.txt'); file1.text = 'Hola\n'
        def file2 = layer.resolve('ciao.txt'); file2.text = 'Ciao\n'
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello_docker.txt
        '''.stripIndent()
        and:
        def l1 = new Packer().layer(layer, [file1, file2])
        def containerConfig = new ContainerConfig(cmd: ['echo', 'Hola'], layers: [l1])
        and:
        def duration = Duration.ofMinutes(1)
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: TestHelper.containerPlatform(),
                        cacheRepository: cacheRepo,
                        configJson: cfg,
                        containerConfig: containerConfig ,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        maxDuration: duration
                )
                        .withBuildId('1')
        and:
        buildCacheStore.storeBuild(targetImage, new BuildStoreEntry(req, BuildResult.create(req)))
        
        when:
        service.launch(req)
        then:
        service
                .buildResult(targetImage)
                .get(duration.toSeconds(), TimeUnit.SECONDS)
                .succeeded()

        cleanup:
        folder?.deleteDir()
    }

}
