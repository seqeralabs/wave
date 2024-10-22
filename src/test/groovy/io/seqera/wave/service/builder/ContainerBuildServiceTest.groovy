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

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.service.builder.impl.BuildStateStoreImpl
import io.seqera.wave.service.builder.impl.ContainerBuildServiceImpl
import io.seqera.wave.service.inspect.ContainerInspectServiceImpl
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.service.scan.ScanStrategy
import io.seqera.wave.test.TestHelper
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.ContainerHelper
import io.seqera.wave.util.Packer
import io.seqera.wave.util.TemplateRenderer
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MicronautTest(environments = ['build-service-test'])
class ContainerBuildServiceTest extends Specification {

    @Primary
    @Singleton
    @Requires(env = 'build-service-test')
    static class FakeBuildStrategy extends BuildStrategy {

        @Override
        void build(String jobName, BuildRequest request) {
            // do nothing
            log.debug "Running fake build job=$jobName - request=$request"
        }

        @Override
        InputStream getLogs(String podName) {
            return "logs for pod name"
        }
    }

    @Primary
    @Singleton
    @Requires(env = 'build-service-test')
    static class FakeScanStrategy extends ScanStrategy {

        @Override
        void scanContainer(String jobName, ScanRequest request) {
            // do nothing
            log.debug "Running fake scan job=$jobName - request=$request"
        }
    }


    @Inject ContainerBuildServiceImpl service
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject ContainerInspectServiceImpl dockerAuthService
    @Inject HttpClientConfig httpClientConfig
    @Inject BuildConfig buildConfig
    @Inject BuildStateStoreImpl buildCacheStore
    @Inject PersistenceService persistenceService
    @Inject JobService jobService

    def 'should save build docker build file' () {
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
        def condaFile = '''
                dependencies:
                  - salmon=1.6.0
                '''
        and:
        def containerId = ContainerHelper.makeContainerId(dockerFile, condaFile, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, condaFile, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        condaFile: condaFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: TestHelper.containerPlatform(),
                        cacheRepository: cacheRepo,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        maxDuration: Duration.ofMinutes(1),
                        buildId: "${containerId}_1",
                )
        and:
        def store = Mock(BuildStateStore)
        def jobService = Mock(JobService)
        def builder = new ContainerBuildServiceImpl(buildStore: store, buildConfig: buildConfig, jobService: jobService)
        def RESPONSE = Mock(JobSpec)
          
        when:
        builder.launch(req)
      
        then:
        1 * jobService.launchBuild(req) >> RESPONSE
        and:
        req.workDir.resolve('Containerfile').text == new TemplateRenderer().render(dockerFile, [:])
        req.workDir.resolve('context/conda.yml').text == condaFile

        cleanup:
        folder?.deleteDir()
    }

    def 'should resolve docker file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def builder = new ContainerBuildServiceImpl()
        def buildRepo = buildConfig.defaultBuildRepository
        and:
        def dockerFile = 'FROM something; {{foo}}'
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: ContainerPlatform.of('amd64'),
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        buildId: "${containerId}_1",
                )

        when:
        def result = builder.containerFile0(req, null)
        then:
        result == 'FROM something; {{foo}}'

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
        def containerId = ContainerHelper.makeContainerId(containerFile, null, ContainerPlatform.of('amd64'), 'buildRepo', null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.SINGULARITY, 'foo.com/repo', containerId, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: containerFile,
                        workspace: folder,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: ContainerPlatform.of('amd64'),
                        format: BuildFormat.SINGULARITY,
                        startTime: Instant.now(),
                        buildId: "${containerId}_1",
                )

        when:
        def result = builder.containerFile0(req, Path.of('/some/context/'))
        then:
        result == '''\
        BootStrap: docker
        Format: ubuntu
        %files
          /some/context/nf-1234/* /
        '''.stripIndent()

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
            def body = cl.location.bytes
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
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, ContainerPlatform.of('amd64'), buildRepo, null)
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        workspace: Path.of('/wsp'),
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: ContainerPlatform.of('amd64'),
                        configJson: '{"config":"json"}',
                        containerConfig: config ,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        buildId: "${containerId}_1",
                )

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
        def containerId = 'container1234'
        final request = new BuildRequest(
                containerId: containerId,
                containerFile: 'test',
                condaFile: 'test',
                workspace: Path.of("."),
                targetImage: 'docker.io/my/repo:container1234',
                identity: PlatformId.NULL,
                platform: ContainerPlatform.of('amd64'),
                configJson: '{"config":"json"}',
                scanId: 'scan12345',
                format: BuildFormat.DOCKER,
                startTime: Instant.now(),
                buildId: "${containerId}_1",
        )

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

    def 'should return only the host name' () {
        expect:
        ContainerInspectServiceImpl.host0(CONTAINER) == EXPECTED
        where:
        CONTAINER           | EXPECTED
        'docker.io'         | 'docker.io'
        'docker.io/foo/'    | 'docker.io'
        'foo/bar'           | 'docker.io'
        'quay.io/foo/bar'   | 'quay.io'
    }

    def 'should handle job completion event and update build store'() {
        given:
        def mockBuildStore = Mock(BuildStateStore)
        def mockProxyService = Mock(RegistryProxyService)
        def mockEventPublisher = Mock(ApplicationEventPublisher<BuildEvent>)
        def service = new ContainerBuildServiceImpl(buildStore: mockBuildStore, proxyService: mockProxyService, eventPublisher: mockEventPublisher, buildConfig: buildConfig)
        def job = JobSpec.build('1', 'operationName', Instant.now(), Duration.ofMinutes(1), Path.of('/work/dir'))
        def state = JobState.succeeded('logs')
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def build = new BuildEntry(req, res)

        when:
        service.onJobCompletion(job, build, state)

        then:
        1 * mockBuildStore.storeBuild('1', _)
        and:
        1 * mockProxyService.getImageDigest(_, _) >> 'digest'
        and:
        1 * mockEventPublisher.publishEvent(_)
    }

    def 'should handle job error event and update build store'() {
        given:
        def mockBuildStore = Mock(BuildStateStore)
        def mockProxyService = Mock(RegistryProxyService)
        def mockEventPublisher = Mock(ApplicationEventPublisher<BuildEvent>)
        def service = new ContainerBuildServiceImpl(buildStore: mockBuildStore, proxyService: mockProxyService, eventPublisher: mockEventPublisher, buildConfig: buildConfig)
        def job = JobSpec.build('1', 'operationName', Instant.now(), Duration.ofMinutes(1), Path.of('/work/dir'))
        def error = new Exception('error')
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def build = new BuildEntry(req, res)

        when:
        service.onJobException(job, build, error)

        then:
        1 * mockBuildStore.storeBuild('1', _)
        and:
        1 * mockEventPublisher.publishEvent(_)
    }

    def 'should handle job timeout event and update build store'() {
        given:
        def mockBuildStore = Mock(BuildStateStore)
        def mockProxyService = Mock(RegistryProxyService)
        def mockEventPublisher = Mock(ApplicationEventPublisher<BuildEvent>)
        def service = new ContainerBuildServiceImpl(buildStore: mockBuildStore, proxyService: mockProxyService, eventPublisher: mockEventPublisher, buildConfig: buildConfig)
        def job = JobSpec.build('1', 'operationName', Instant.now(), Duration.ofMinutes(1), Path.of('/work/dir'))
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1)
        )
        def build = new BuildEntry(req, res)

        when:
        service.onJobTimeout(job, build)

        then:
        1 * mockBuildStore.storeBuild('1', _)
        and:
        1 * mockEventPublisher.publishEvent(_)
    }

}
