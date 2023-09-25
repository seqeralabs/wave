/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.cleanup.CleanupStrategy
import io.seqera.wave.service.inspect.ContainerInspectServiceImpl
import io.seqera.wave.storage.reader.ContentReaderFactory
import io.seqera.wave.tower.User
import io.seqera.wave.util.Packer
import io.seqera.wave.util.SpackHelper
import io.seqera.wave.util.TemplateRenderer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MicronautTest
class ContainerBuildServiceTest extends Specification {

    @Inject ContainerBuildServiceImpl service
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject ContainerInspectServiceImpl dockerAuthService

    @Value('${wave.build.repo}') String buildRepo
    @Value('${wave.build.cache}') String cacheRepo

    @Inject HttpClientConfig httpClientConfig

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should build & push container to aws' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, cacheRepo, null, null,null,null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, BuildFormat.DOCKER,Mock(User), null, null, ContainerPlatform.of('amd64'), cfg, cacheRepo, null, "", null)

        when:
        def result = service.launch(REQ)
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
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "docker.io/pditommaso/wave-tests"
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, null, null,null,null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),cfg, null, null, null, null)

        when:
        def result = service.launch(REQ)
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
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "quay.io/pditommaso/wave-tests"
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, null, null, null, null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),cfg, null, null, "", null)

        when:
        def result = service.launch(REQ)
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
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "seqeralabs.azurecr.io/wave-tests"
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, null, null, null, null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),cfg, null, null, "", null)

        when:
        def result = service.launch(REQ)
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
                  concretizer: {unify: true, reuse: false}
                '''
        and:
        def spackConfig = new SpackConfig(cacheBucket: 's3://bucket/cache', secretMountPath: '/mnt/secret')
        def REQ = new BuildRequest(dockerFile, folder, 'box:latest', condaFile, spackFile, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'), cfg, null, null, "", null)
        and:
        def store = Mock(BuildStore)
        def strategy = Mock(BuildStrategy)
        def builder = new ContainerBuildServiceImpl(buildStrategy: strategy, buildStore: store, statusDuration: DURATION, spackConfig:spackConfig, cleanup: new CleanupStrategy())
        def RESPONSE = Mock(BuildResult)

        when:
        def result = builder.launch(REQ)
        then:
        1 * strategy.build(REQ) >> RESPONSE
        1 * store.storeBuild(REQ.targetImage, RESPONSE, DURATION) >> null
        and:
        REQ.workDir.resolve('Containerfile').text == new TemplateRenderer().render(dockerFile, [spack_cache_bucket:'s3://bucket/cache', spack_key_file:'/mnt/secret'])
        REQ.workDir.resolve('context/conda.yml').text == condaFile
        REQ.workDir.resolve('context/spack.yaml').text == spackFile
        and:
        result == RESPONSE

        cleanup:
        folder?.deleteDir()
    }

    def 'should resolve docker file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def builder = new ContainerBuildServiceImpl()
        and:
        def dockerFile = 'FROM something; {{foo}}'
        def REQ = new BuildRequest(dockerFile, folder, 'box:latest', null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'), null, null, null, "", null)
        and:
        def spack = Mock(SpackConfig)

        when:
        def result = builder.containerFile0(REQ, null, spack)
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
        def REQ = new BuildRequest(dockerFile, folder, 'box:latest', null, spackFile, BuildFormat.DOCKER, Mock(User),null, null,  ContainerPlatform.of('amd64'), null, null, null, "", null)
        and:
        def spack = Mock(SpackConfig)

        when:
        def result = builder.containerFile0(REQ, null, spack)
        then:
        1* spack.getCacheBucket() >> 's3://bucket/cache'
        1* spack.getSecretMountPath() >> '/mnt/key'
        1* spack.getBuilderImage() >> 'spack-builder:2.0'
        1* spack.getRunnerImage() >> 'ubuntu:22.04'
        and:
        result.contains('FROM spack-builder:2.0 as builder')
        result.contains('spack config add packages:all:target:[x86_64]')
        result.contains('spack mirror add seqera-spack s3://bucket/cache')
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
        def REQ = new BuildRequest(dockerFile, folder, 'box:latest', null, spackFile, BuildFormat.SINGULARITY, Mock(User),null, null,  ContainerPlatform.of('amd64'), null, null, null, "", null)
        and:
        def spack = Mock(SpackConfig)

        when:
        def result = builder.containerFile0(REQ, context, spack)
        then:
        1* spack.getCacheBucket() >> 's3://bucket/cache'
        1* spack.getSecretMountPath() >> '/mnt/key'
        1* spack.getBuilderImage() >> 'spack-builder:2.0'
        1* spack.getRunnerImage() >> 'ubuntu:22.04'
        and:
        result.contains('Bootstrap: docker\n' +
                'From: spack-builder:2.0\n' +
                'Stage: build')
        result.contains('spack config add packages:all:target:[x86_64]')
        result.contains('spack mirror add seqera-spack s3://bucket/cache')
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
        def REQ = new BuildRequest(containerFile, folder, 'box:latest', null, null, BuildFormat.SINGULARITY, Mock(User),null, null,  ContainerPlatform.of('amd64'), null, null, null, "", null)

        when:
        def result = builder.containerFile0(REQ, Path.of('/some/context/'), null)
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
        buildRepo = "docker.io/pditommaso/wave-tests"
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, null, null,null,null)
        def REQ = new BuildRequest(dockerFile, context, buildRepo, null, null, BuildFormat.DOCKER, Mock(User), containerConfig, null, ContainerPlatform.of('amd64'),cfg, null, null, null, null)

        when:
        def result = service.launch(REQ)
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
        and:
        def service = new ContainerBuildServiceImpl(httpClientConfig: httpClientConfig)

        when:
        service.saveBuildContext(context, target)
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
        def req = new BuildRequest('from foo', Path.of('/wsp'), 'quay.io/org/name', null, null, BuildFormat.DOCKER, Mock(User), config, null, ContainerPlatform.of('amd64'),'{auth}', null, null, "127.0.0.1", null)
        and:
        def service = new ContainerBuildServiceImpl(httpClientConfig: httpClientConfig)

        when:
        service.saveLayersToContext(req, folder)
        then:
        Files.exists(folder.resolve("layer-${l1.gzipDigest.replace(/sha256:/,'')}.tar.gz"))
        Files.exists(folder.resolve("layer-${l2.gzipDigest.replace(/sha256:/,'')}.tar.gz"))

        cleanup:
        folder?.deleteDir()
        server?.stop(0)
    }

}
