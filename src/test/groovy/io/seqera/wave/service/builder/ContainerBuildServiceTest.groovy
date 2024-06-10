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
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.util.logging.Slf4j
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.store.BuildRecordStore
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.inspect.ContainerInspectServiceImpl
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.storage.reader.ContentReaderFactory
import io.seqera.wave.test.RedisTestContainer
import io.seqera.wave.test.SurrealDBTestContainer
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.Packer
import io.seqera.wave.util.SpackHelper
import io.seqera.wave.util.TemplateRenderer
import jakarta.inject.Inject
import io.seqera.wave.util.ContainerHelper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MicronautTest

class ContainerBuildServiceTest extends Specification implements RedisTestContainer, SurrealDBTestContainer{

    @Inject ContainerBuildServiceImpl service
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject ContainerInspectServiceImpl dockerAuthService
    @Inject HttpClientConfig httpClientConfig
    @Inject BuildConfig buildConfig
    @Inject BuildRecordStore buildRecordStore
    @Inject PersistenceService persistenceService


    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should build & push container to aws' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = buildConfig.defaultBuildRepository
        def cacheRepo = buildConfig.defaultCacheRepository
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, cacheRepo, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerFile, null, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", cfg, null,null , null, null, BuildFormat.DOCKER)
        .withBuildId('1')

        when:
        def result = service.launch(req)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('DOCKER_USER') && System.getenv('DOCKER_PAT')})
    def 'should build & push container to docker.io' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = "docker.io/pditommaso/wave-tests"
        def cacheRepo = buildConfig.defaultCacheRepository
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerFile, null, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", cfg, null,null , null, null, BuildFormat.DOCKER)
                .withBuildId('1')

        when:
        def result = service.launch(req)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('QUAY_USER') && System.getenv('QUAY_PAT')})
    def 'should build & push container to quay.io' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = buildConfig.defaultBuildRepository
        def cacheRepo = buildConfig.defaultCacheRepository
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "quay.io/pditommaso/wave-tests"
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerFile, null, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", cfg, null,null , null, null, BuildFormat.DOCKER)
                .withBuildId('1')

        when:
        def result = service.launch(req)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

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
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerFile, null, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", cfg, null,null , null, null, BuildFormat.DOCKER)
                .withBuildId('1')

        when:
        def result = service.launch(req)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    def 'should save build docker build file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = buildConfig.defaultBuildRepository
        def cacheRepo = buildConfig.defaultCacheRepository
        def DURATION = Duration.ofDays(1)
        and:
        def cfg = 'some credentials'
        def dockerFile = '''
                FROM busybox
                RUN echo Hello > hello.txt
                RUN {{spack_cache_bucket}} {{spack_key_file}}
                '''.stripIndent()
        and:
        def condaFile = '''
                dependencies:
                  - salmon=1.6.0
                '''
        and:
        def spackFile = '''
                spack:
                  specs: [bwa@0.7.15, salmon@1.1.1]
                  concretizer: {unify: true, reuse: true}
                '''
        and:
        def spackConfig = new SpackConfig(cacheBucket: 's3://bucket/cache', secretMountPath: '/mnt/secret')
        def containerId = ContainerHelper.makeContainerId(dockerFile, condaFile, spackFile, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, condaFile, spackFile, null)
        def req = new BuildRequest(containerId, dockerFile, condaFile, spackFile, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", null, null,null , null, null, BuildFormat.DOCKER)
                .withBuildId('1')
        and:
        def store = Mock(BuildStore)
        def strategy = Mock(BuildStrategy)
        def builder = new ContainerBuildServiceImpl(buildStrategy: strategy, buildStore: store, buildConfig: buildConfig, spackConfig:spackConfig, cleanup: new CleanupStrategy(buildConfig: buildConfig))
        def RESPONSE = Mock(BuildResult)

        when:
        def result = builder.launch(req)
        then:
        1 * strategy.build(req) >> RESPONSE
        1 * store.storeBuild(req.targetImage, RESPONSE, DURATION) >> null
        and:
        req.workDir.resolve('Containerfile').text == new TemplateRenderer().render(dockerFile, [spack_cache_bucket:'s3://bucket/cache', spack_key_file:'/mnt/secret'])
        req.workDir.resolve('context/conda.yml').text == condaFile
        req.workDir.resolve('context/spack.yaml').text == spackFile
        and:
        result == RESPONSE

        cleanup:
        folder?.deleteDir()
    }

    def 'should resolve docker file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def builder = new ContainerBuildServiceImpl()
        def buildRepo = buildConfig.defaultBuildRepository
        def cacheRepo = buildConfig.defaultCacheRepository
        and:
        def dockerFile = 'FROM something; {{foo}}'
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerFile, null, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", null, null,null , null, null, BuildFormat.DOCKER)
                .withBuildId('1')
        and:
        def spack = Mock(SpackConfig)

        when:
        def result = builder.containerFile0(req, null, spack)
        then:
        0* spack.getCacheMountPath() >> null
        0* spack.getSecretMountPath() >> null
        0* spack.getBuilderImage() >> null
        and:
        result == 'FROM something; {{foo}}'

        cleanup:
        folder?.deleteDir()
    }

    def 'should resolve docker file with spack config' () {
        given:
        def folder = Files.createTempDirectory('test')
        def builder = new ContainerBuildServiceImpl()
        and:
        def dockerFile = SpackHelper.builderDockerTemplate()
        def spackFile = 'some spack packages'
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, spackFile, ContainerPlatform.of('amd64'), 'buildRepo', null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, 'foo.com/repo', containerId, null, spackFile, null)
        def req = new BuildRequest(containerId, dockerFile, null, spackFile, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), 'cacheRepo', "10.20.30.40", null, null,null , null, null, BuildFormat.DOCKER)
                .withBuildId('1')
        and:
        def spack = Mock(SpackConfig)

        when:
        def result = builder.containerFile0(req, null, spack)
        then:
        1* spack.getCacheBucket() >> 's3://bucket/cache'
        1* spack.getSecretMountPath() >> '/mnt/key'
        1* spack.getBuilderImage() >> 'spack-builder:2.0'
        1* spack.getRunnerImage() >> 'ubuntu:jammy'
        and:
        result.contains('FROM spack-builder:2.0 as builder')
        result.contains('spack -e . config add packages:all:target:[x86_64]')
        result.contains('spack -e . mirror add seqera_spack s3://bucket/cache')
        result.contains('fingerprint="$(spack gpg trust /mnt/key 2>&1 | tee /dev/stderr | sed -nr "s/^gpg: key ([0-9A-F]{16}): secret key imported$/\\1/p")"')

        cleanup:
        folder?.deleteDir()
    }

    def 'should resolve singularity file with spack config' () {
        given:
        def folder = Files.createTempDirectory('test')
        def builder = new ContainerBuildServiceImpl()
        and:
        def context = Path.of('/some/context/dir')
        def dockerFile = SpackHelper.builderSingularityTemplate()
        def spackFile = 'some spack packages'
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, spackFile, ContainerPlatform.of('amd64'), 'buildRepo', null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.SINGULARITY, 'foo.com/repo', containerId, null, spackFile, null)
        def req = new BuildRequest(containerId, dockerFile, null, spackFile, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), 'cacheRepo', "10.20.30.40", null, null,null , null, null, BuildFormat.SINGULARITY)
                .withBuildId('1')
        and:
        def spack = Mock(SpackConfig)

        when:
        def result = builder.containerFile0(req, context, spack)
        then:
        1* spack.getCacheBucket() >> 's3://bucket/cache'
        1* spack.getSecretMountPath() >> '/mnt/key'
        1* spack.getBuilderImage() >> 'spack-builder:2.0'
        1* spack.getRunnerImage() >> 'ubuntu:jammy'
        and:
        result.contains('Bootstrap: docker\n' +
                'From: spack-builder:2.0\n' +
                'Stage: build')
        result.contains('spack -e . config add packages:all:target:[x86_64]')
        result.contains('spack -e . mirror add seqera_spack s3://bucket/cache')
        result.contains('fingerprint="$(spack gpg trust /mnt/key 2>&1 | tee /dev/stderr | sed -nr "s/^gpg: key ([0-9A-F]{16}): secret key imported$/\\1/p")"')
        result.contains('/some/context/dir/spack.yaml /opt/spack-env/spack.yaml')

        cleanup:
        folder?.deleteDir()
    }

    def 'should replace context path' () {
        given:
        def folder = Path.of('/some/work/dir')
        def containerFile = '''\
        BootStrap: docker
        Format: ubuntu
        %files
          {{wave_context_dir}}/nf-1234/* /
        '''.stripIndent()
        and:
        def builder = new ContainerBuildServiceImpl()
        def containerId = ContainerHelper.makeContainerId(containerFile, null, null, ContainerPlatform.of('amd64'), 'buildRepo', null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.SINGULARITY, 'foo.com/repo', containerId, null, null, null)
        def req = new BuildRequest(containerId, containerFile, null, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), 'cacheRepo', "10.20.30.40", null, null,null , null, null, BuildFormat.SINGULARITY).withBuildId('1')

        when:
        def result = builder.containerFile0(req, Path.of('/some/context/'), null)
        then:
        result == '''\
        BootStrap: docker
        Format: ubuntu
        %files
          /some/context/nf-1234/* /
        '''.stripIndent()

    }
    @Requires({System.getenv('DOCKER_USER') && System.getenv('DOCKER_PAT')})
    def 'should build & push container to docker.io with local layers' () {
        given:
        def folder = Files.createTempDirectory('test')
        def buildRepo = "docker.io/pditommaso/wave-tests"
        def cacheRepo = buildConfig.defaultCacheRepository
        def context = Files.createDirectories(folder.resolve('context'))
        def layer = Files.createDirectories(folder.resolve('layer'))
        def file1 = layer.resolve('hola.txt'); file1.text = 'Hola\n'
        def file2 = layer.resolve('ciao.txt'); file2.text = 'Ciao\n'
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def l1 = new Packer().layer(layer, [file1, file2])
        def containerConfig = new ContainerConfig(cmd: ['echo', 'Hola'], layers: [l1])
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, Mock(PlatformId))
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerFile, null, null, folder, targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), cacheRepo, "10.20.30.40", cfg, null,containerConfig , null, null, BuildFormat.DOCKER).withBuildId('1')

        when:
        def result = service.launch(req)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }


    def 'should untar build context' () {
        given:
        def folder = Files.createTempDirectory('test')
        def source = folder.resolve('source')
        def target = folder.resolve('target')
        Files.createDirectory(source)
        Files.createDirectory(target)
        and:
        source.resolve('foo.txt').text  = 'Foo'
        source.resolve('bar.txt').text  = 'Bar'
        and:
        def layer = new Packer().layer(source)
        def context = BuildContext.of(layer)

        when:
        service.saveBuildContext(context, target, Mock(PlatformId))
        then:
        target.resolve('foo.txt').text == 'Foo'
        target.resolve('bar.txt').text == 'Bar'

        cleanup:
        folder?.deleteDir()
    }


    def 'should save layers to context dir' () {
        given:
        def folder = Files.createTempDirectory('test')
        def file1 = folder.resolve('file1'); file1.text = "I'm file one"
        def file2 = folder.resolve('file2'); file2.text = "I'm file two"
        and:
        def cl = new Packer().layer(folder, [file1])
        def l1 = new ContainerLayer(location: "http://localhost:9901/some.tag.gz", tarDigest: cl.tarDigest, gzipDigest: cl.gzipDigest, gzipSize: cl.gzipSize)
        and:
        def l2 = new Packer().layer(folder, [file2])
        def config = new ContainerConfig(layers: [l1,l2])

        and:
        HttpHandler handler = { HttpExchange exchange ->
            def body = ContentReaderFactory.of(cl.location).readAllBytes()
            exchange.getResponseHeaders().add("Content-Type", "application/tar+gzip")
            exchange.sendResponseHeaders(200, body.size())
            exchange.getResponseBody() << body
            exchange.getResponseBody().close()

        }
        and:
        HttpServer server = HttpServer.create(new InetSocketAddress(9901), 0);
        server.createContext("/", handler);
        server.start()
        and:
        def dockerFile = 'from foo'
        def buildRepo = 'quay.io/org/name'
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null, null)
        def req = new BuildRequest(containerId, dockerFile, null, null, Path.of('/wsp'), targetImage, Mock(PlatformId), ContainerPlatform.of('amd64'), 'cacheRepo', "10.20.30.40", '{"config":"json"}', null,config , null, null, BuildFormat.DOCKER).withBuildId('1')

        when:
        service.saveLayersToContext(req, folder)
        then:
        Files.exists(folder.resolve("layer-${l1.gzipDigest.replace(/sha256:/,'')}.tar.gz"))
        Files.exists(folder.resolve("layer-${l2.gzipDigest.replace(/sha256:/,'')}.tar.gz"))

        cleanup:
        folder?.deleteDir()
        server?.stop(0)
    }

    void "an event insert a build"() {
        given:
        final request = new BuildRequest(
                'container1234',
                'test',
                'test',
                'test',
                Path.of("."),
                'docker.io/my/repo:container1234',
                PlatformId.NULL,
                ContainerPlatform.of('amd64'),
                'docker.io/my/cache',
                '127.0.0.1',
                '{"config":"json"}',
                null,
                null,
                'scan12345',
                null,
                BuildFormat.DOCKER
        ).withBuildId('123')

        and:
        def result = new BuildResult(request.buildId, 0, "content", Instant.now(), Duration.ofSeconds(1), 'abc123')
        def event = new BuildEvent(request, result)

        when:
        service.onBuildEvent(event)

        then:
        def record = service.getBuildRecord(request.buildId)
        record.buildId == request.buildId
        record.digest == 'abc123'
    }

    void "should create build record in redis"() {
        given:
        final request = new BuildRequest(
                'container1234',
                'test',
                'test',
                'test',
                Path.of("."),
                'docker.io/my/repo:container1234',
                PlatformId.NULL,
                ContainerPlatform.of('amd64'),
                'docker.io/my/cache',
                '127.0.0.1',
                '{"config":"json"}',
                null,
                null,
                'scan12345',
                null,
                BuildFormat.DOCKER
        ).withBuildId('123')

        and:
        def result = BuildResult.completed(request.buildId, 1, 'Hello', Instant.now().minusSeconds(60), 'xyz')

        and:
        def build = WaveBuildRecord.fromEvent(new BuildEvent(request, result))

        when:
        service.createBuildRecord(build.buildId, build)

        then:
        def record = buildRecordStore.getBuildRecord(request.buildId)
        record.buildId == request.buildId
        record.digest == 'xyz'
    }

    void "should save build record in redis and surrealdb"() {
        given:
        final request = new BuildRequest(
                'container1234',
                'test',
                'test',
                'test',
                Path.of("."),
                'docker.io/my/repo:container1234',
                PlatformId.NULL,
                ContainerPlatform.of('amd64'),
                'docker.io/my/cache',
                '127.0.0.1',
                '{"config":"json"}',
                null,
                null,
                'scan12345',
                null,
                BuildFormat.DOCKER
        ).withBuildId('123')

        and:
        def result = new BuildResult(request.buildId, 0, "content", Instant.now(), Duration.ofSeconds(1), 'abc123')
        def event = new BuildEvent(request, result)

        when:
        service.saveBuildRecord(event)

        then:
        def record = persistenceService.loadBuild(request.buildId)
        record.buildId == request.buildId
        record.digest == 'abc123'

        and:
        def record2 = buildRecordStore.getBuildRecord(request.buildId)
        record2.buildId == request.buildId
        record2.digest == 'abc123'
    }
}
