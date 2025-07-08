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

import spock.lang.Shared
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
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
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
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanStrategy
import io.seqera.wave.service.stream.StreamService
import io.seqera.wave.test.AwsS3TestContainer
import io.seqera.wave.test.TestHelper
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.ContainerHelper
import io.seqera.wave.util.Packer
import io.seqera.wave.util.TemplateRenderer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MicronautTest(environments = ['build-service-test'])
class ContainerBuildServiceTest extends Specification implements AwsS3TestContainer {

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
        List<String> singularityLaunchCmd(BuildRequest req) {
            return ['singularity', 'fake', 'cmd']
        }
    }

    @Primary
    @Singleton
    @Requires(env = 'build-service-test')
    static class FakeScanStrategy extends ScanStrategy {

        @Override
        void scanContainer(String jobName, ScanEntry request) {
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
    @Inject ApplicationEventPublisher<BuildEvent> eventPublisher

    @Shared
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    def setupSpec() {
        def s3Client = S3Client.builder()
                .endpointOverride(URI.create("http://${awsS3HostName}:${awsS3Port}"))
                .region(Region.EU_WEST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accesskey", "secretkey")))
                .forcePathStyle(true)
                .build()
        def inputStreamMapper = Mock(InputStreamMapper)
        def storageBucket = "test-bucket"
        s3Client.createBucket { it.bucket(storageBucket) }
        def configuration = new AwsS3Configuration('build-logs')
        configuration.setBucket(storageBucket)
        objectStorageOperations = new AwsS3Operations(configuration, s3Client, inputStreamMapper)
    }

    def 'should save build docker build file' () {
        given:
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
        def containerId = ContainerHelper.makeContainerId(dockerFile, condaFile, ContainerPlatform.of('amd64'), buildRepo, null, Mock(ContainerConfig))
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, condaFile, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        condaFile: condaFile,
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
        def builder = new ContainerBuildServiceImpl(objectStorageOperations: objectStorageOperations, buildStore: store, buildConfig: buildConfig, jobService: jobService, persistenceService: persistenceService, eventPublisher: eventPublisher)
        def RESPONSE = Mock(JobSpec)
          
        when:
        builder.launch(req)
      
        then:
        1 * jobService.launchBuild(req) >> RESPONSE
        and:
        objectStorageOperations.retrieve("$req.workDir/Containerfile").map(ObjectStorageEntry::getInputStream).get().text
                == new TemplateRenderer().render(dockerFile, [:])
        objectStorageOperations.retrieve("$req.workDir/context/conda.yml").map(ObjectStorageEntry::getInputStream).get().text
                == condaFile
    }

    def 'should resolve docker file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def builder = new ContainerBuildServiceImpl()
        def buildRepo = buildConfig.defaultBuildRepository
        and:
        def dockerFile = 'FROM something; {{foo}}'
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, ContainerPlatform.of('amd64'), buildRepo, null, Mock(ContainerConfig))
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
        def result = builder.containerFile0(req)
        then:
        result == 'FROM something; {{foo}}'

        cleanup:
        folder?.deleteDir()
    }

    def 'should replace context path' () {
        given:
        def containerFile = '''\
        BootStrap: docker
        Format: ubuntu
        %files
          {{wave_context_dir}}/nf-1234/* /
        '''.stripIndent()
        and:
        def builder = new ContainerBuildServiceImpl(buildConfig: buildConfig)
        def containerId = ContainerHelper.makeContainerId(containerFile, null, ContainerPlatform.of('amd64'), 'buildRepo', null, Mock(ContainerConfig))
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.SINGULARITY, 'foo.com/repo', containerId, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: containerFile,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: ContainerPlatform.of('amd64'),
                        format: BuildFormat.SINGULARITY,
                        startTime: Instant.now(),
                        buildId: "${containerId}_1",
                )

        when:
        def result = builder.containerFile0(req)
        then:
        result == '''\
        BootStrap: docker
        Format: ubuntu
        %files
          /fusion/s3/nextflow-ci/wave-build/workspace/89356dcc8b4578f4_1/context/nf-1234/* /
        '''.stripIndent()

    }

    def 'should untar build context' () {
        given:
        def gzippedTarBytes = createGzippedTar()
        def stream = new ByteArrayInputStream(gzippedTarBytes)
        def streamService = Mock(StreamService)
        def httpClientConfig = new HttpClientConfig(retryDelay: Duration.ofSeconds(1))
        and:
        streamService.stream(_, _) >> stream
        and:
        def folder = Files.createTempDirectory('test')
        def source = folder.resolve('source')
        def target = "context"
        Files.createDirectory(source)
        and:
        source.resolve('foo.txt').text  = 'Foo'
        source.resolve('bar.txt').text  = 'Bar'
        and:
        def layer = new Packer().layer(source)
        def context = BuildContext.of(layer)
        and:
        def builder = new ContainerBuildServiceImpl(objectStorageOperations: objectStorageOperations, streamService: streamService, httpClientConfig: httpClientConfig)

        when:
        builder.saveBuildContext(context, target, Mock(PlatformId))
        then:
        !objectStorageOperations.listObjects().isEmpty()

        cleanup:
        folder?.deleteDir()
    }

    def 'should save build context successfully'() {
        given:
        def buildContext = Mock(BuildContext)
        buildContext.location >> 'http://localhost:9901/some.tag.gz'
        def contextDir = '/build/context'
        def identity = Mock(PlatformId)
        def gzippedTarBytes = createGzippedTar()
        def stream = new ByteArrayInputStream(gzippedTarBytes)
        def streamService = Mock(StreamService)
        def httpClientConfig = new HttpClientConfig(retryDelay: Duration.ofSeconds(1))
        def mockObjectStorageOperations = Mock(ObjectStorageOperations)
        and:
        streamService.stream(buildContext.location, identity) >> stream
        and:
        def builder = new ContainerBuildServiceImpl(objectStorageOperations: mockObjectStorageOperations, streamService: streamService, httpClientConfig: httpClientConfig)

        when:
        builder.saveBuildContext(buildContext, contextDir, identity)

        then:
        1 * mockObjectStorageOperations.upload(_)
    }

    static byte[] createGzippedTar(String fileName = "file.txt", String content = "hello") {
        def baos = new ByteArrayOutputStream()
        def gzipOut = new GzipCompressorOutputStream(baos)
        def tarOut = new TarArchiveOutputStream(gzipOut)
        def entry = new TarArchiveEntry(fileName)
        entry.size = content.bytes.length
        tarOut.putArchiveEntry(entry)
        tarOut.write(content.bytes)
        tarOut.closeArchiveEntry()
        tarOut.close()
        gzipOut.close()
        baos.toByteArray()
    }

    def 'should save layers to context dir' () {
        given:
        def gzippedTarBytes = createGzippedTar()
        def stream = new ByteArrayInputStream(gzippedTarBytes)
        def streamService = Mock(StreamService)
        def httpClientConfig = new HttpClientConfig(retryDelay: Duration.ofSeconds(1))
        and:
        streamService.stream(_, _) >> stream
        and:
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
        def containerId = ContainerHelper.makeContainerId(dockerFile, null, ContainerPlatform.of('amd64'), buildRepo, null, Mock(ContainerConfig))
        def targetImage = ContainerHelper.makeTargetImage(BuildFormat.DOCKER, buildRepo, containerId, null, null)
        def req =
                new BuildRequest(
                        containerId: containerId,
                        containerFile: dockerFile,
                        targetImage: targetImage,
                        identity: Mock(PlatformId),
                        platform: ContainerPlatform.of('amd64'),
                        configJson: '{"config":"json"}',
                        containerConfig: config ,
                        format: BuildFormat.DOCKER,
                        startTime: Instant.now(),
                        buildId: "${containerId}_1",
                )

        def builder = new ContainerBuildServiceImpl(objectStorageOperations: objectStorageOperations, httpClientConfig: httpClientConfig, streamService: streamService)

        when:
        builder.saveLayersToContext(req, "context")
        then:
        objectStorageOperations.exists("context/layer-${l1.gzipDigest.replace(/sha256:/,'')}.tar.gz")
        objectStorageOperations.exists("context/layer-${l2.gzipDigest.replace(/sha256:/,'')}.tar.gz")

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
        def entry = new BuildEntry(request, result)

        when:
        service.handleBuildCompletion(entry)

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
        def buildStore = Mock(BuildStateStore)
        def proxyService = Mock(RegistryProxyService)
        def persistenceService = Mock(PersistenceService)
        def scanService = Mock(ContainerScanService)
        def eventPublisher = Mock(ApplicationEventPublisher<BuildEvent>)
        def service = new ContainerBuildServiceImpl(buildStore: buildStore, proxyService: proxyService, eventPublisher: eventPublisher, persistenceService: persistenceService, scanService:scanService, buildConfig: buildConfig)
        def job = JobSpec
                .build('1', 'operationName', Instant.now(), Duration.ofMinutes(1), '/work/dir')
                .withLaunchTime(Instant.now())
        def state = JobState.succeeded('logs')
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1),
                identity: PlatformId.NULL
        )
        def build = new BuildEntry(req, res)

        when:
        service.onJobCompletion(job, build, state)

        then:
        1 * scanService.scanOnBuild(_) >> null
        and:
        1 * buildStore.storeBuild(req.targetImage, _) >> null
        and:
        1 * proxyService.getImageDigest(_, _) >> 'digest'
        and:
        1 * persistenceService.saveBuildAsync(_) >> null
        and:
        1 * eventPublisher.publishEvent(_)
    }

    def 'should handle job error event and update build store'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def proxyService = Mock(RegistryProxyService)
        def persistenceService = Mock(PersistenceService)
        def eventPublisher = Mock(ApplicationEventPublisher<BuildEvent>)
        def service = new ContainerBuildServiceImpl(buildStore: buildStore, proxyService: proxyService, eventPublisher: eventPublisher, persistenceService:persistenceService, buildConfig: buildConfig)
        def job = JobSpec.build('1', 'operationName', Instant.now(), Duration.ofMinutes(1), '/work/dir')
        def error = new Exception('error')
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1),
                identity: PlatformId.NULL
        )
        def build = new BuildEntry(req, res)

        when:
        service.onJobException(job, build, error)

        then:
        1 * buildStore.storeBuild(req.targetImage, _) >> null
        and:
        1 * persistenceService.saveBuildAsync(_) >> null
        and:
        1 * eventPublisher.publishEvent(_)
    }

    def 'should handle job timeout event and update build store'() {
        given:
        def buildStore = Mock(BuildStateStore)
        def proxyService = Mock(RegistryProxyService)
        def persistenceService = Mock(PersistenceService)
        def eventPublisher = Mock(ApplicationEventPublisher<BuildEvent>)
        def service = new ContainerBuildServiceImpl(buildStore: buildStore, proxyService: proxyService, eventPublisher: eventPublisher, persistenceService:persistenceService, buildConfig: buildConfig)
        def job = JobSpec.build('1', 'operationName', Instant.now(), Duration.ofMinutes(1), '/work/dir')
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'docker.io/foo:0',
                buildId: '1',
                startTime: Instant.now(),
                maxDuration: Duration.ofMinutes(1),
                identity: PlatformId.NULL
        )
        def build = new BuildEntry(req, res)

        when:
        service.onJobTimeout(job, build)

        then:
        1 * buildStore.storeBuild(req.targetImage, _) >> null
        and:
        1 * persistenceService.saveBuildAsync(_) >> null
        and:
        1 * eventPublisher.publishEvent(_)
    }

}
