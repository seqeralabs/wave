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

package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.temporal.ChronoUnit

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerStatusResponse
import io.seqera.wave.api.ImageNameStrategy
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.exchange.DescribeWaveContainerResponse
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildTrack
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.builder.FreezeService
import io.seqera.wave.service.builder.FreezeServiceImpl
import io.seqera.wave.service.inclusion.ContainerInclusionService
import io.seqera.wave.service.inspect.ContainerInspectServiceImpl
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobServiceImpl
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.request.ContainerRequestService
import io.seqera.wave.service.request.TokenData
import io.seqera.wave.service.validation.ValidationService
import io.seqera.wave.service.validation.ValidationServiceImpl
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuthStore
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
@Property(name='wave.build.workspace', value='/some/wsp')
@Property(name='wave.build.repo', value='wave/build')
@Property(name='wave.build.cache', value='wave/build/cache')
class ContainerControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    BuildConfig buildConfig

    @Inject
    JwtAuthStore jwtAuthStore

    @Inject
    ApplicationContext applicationContext

    @Inject
    RegistryProxyService proxyRegistry

    @Inject
    ValidationService validationService

    @MockBean(JobServiceImpl)
    JobService mockJobService() {
        Mock(JobService)
    }

    @MockBean(RegistryProxyService)
    RegistryProxyService mockProxy() {
        Mock(RegistryProxyService) {
            getImageDigest(_,_) >> 'sha256:mock-digest'
        }
    }

    def setup() {
        jwtAuthStore.clear()
    }

    def 'should create request data' () {
        given:
        def controller = new ContainerController(inclusionService: Mock(ContainerInclusionService), registryProxyService: proxyRegistry)

        when:
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest')
        def data = controller.makeRequestData(req, PlatformId.NULL, "")
        then:
        data.containerImage == 'docker.io/library/ubuntu:latest'

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        data = controller.makeRequestData(req, new PlatformId(new User(id: 100), 10), "127.0.0.1")
        then:
        data.containerImage == 'docker.io/library/ubuntu:latest'
        data.identity.userId == 100
        data.identity.workspaceId == 10
        data.containerConfig == cfg
        data.platform == ContainerPlatform.of('arm64')

        when:
        req = new SubmitContainerTokenRequest()
        controller.makeRequestData(req, new PlatformId(new User(id: 100)),"")
        then:
        thrown(BadRequestException)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'ubuntu', containerFile: 'from foo')
        controller.makeRequestData(req, new PlatformId(new User(id: 100)),"")
        then:
        thrown(BadRequestException)

    }

    def 'should create request data with freeze mode' () {
        given:
        def freeze = Mock(FreezeService)
        def containerImage = 'ubuntu:latest'
        and:
        def controller = Spy(new ContainerController(freezeService: freeze, inclusionService: Mock(ContainerInclusionService)))
        and:
        def target = 'docker.io/repo/ubuntu:latest'
        def BUILD = Mock(BuildRequest) {
            getTargetImage() >> target
        }
        and:
        def req = new SubmitContainerTokenRequest(containerImage: containerImage, freeze: true, buildRepository: 'docker.io/foo/bar')

        when:
        def data = controller.makeRequestData(req, PlatformId.NULL, "")
        then:
        1 * freeze.freezeBuildRequest(req, _) >> req.copyWith(containerFile: 'FROM ubuntu:latest')
        1 * controller.makeBuildRequest(_,_,_) >> BUILD
        1 * controller.checkBuild(BUILD,false) >> new BuildTrack('1', target, false, false)
        1 * controller.getContainerDigest(containerImage, PlatformId.NULL) >> 'sha256:12345'
        and:
        data.containerImage == target
        data.succeeded == false

    }

    String encode(String str) {
        str.bytes.encodeBase64().toString()
    }

    String decode(String str) {
        new String(str.decodeBase64())
    }

    def 'should make a build request' () {
        given:
        def builder = Mock(ContainerBuildService)
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def controller = new ContainerController(buildService: builder, inspectService: dockerAuth, registryProxyService: proxyRegistry, buildConfig: buildConfig, inclusionService: Mock(ContainerInclusionService))
        def DOCKER = 'FROM foo'
        def user = new PlatformId(new User(id: 100))
        def cfg = new ContainerConfig()
        def req = new SubmitContainerTokenRequest(
                containerFile: encode(DOCKER),
                containerPlatform: 'arm64',
                containerConfig: cfg)

        when:
        def data = controller.makeRequestData(req, user, "")
        then:
        1 * proxyRegistry.getImageDigest(_) >> null
        1 * builder.buildImage(_) >> new BuildTrack('1', 'wave/build:be9ee6ac1eeff4b5', false, true)
        and:
        data.containerFile == DOCKER
        data.identity.userId == 100
        data.containerImage ==  'wave/build:be9ee6ac1eeff4b5'
        data.containerConfig == cfg
        data.platform.toString() == 'linux/arm64'
        data.buildNew == true
        data.succeeded == true
    }

    def 'should not run a build request if manifest is present' () {
        given:
        def builder = Mock(ContainerBuildService)
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def persistenceService = Mock(PersistenceService)
        def controller = new ContainerController(buildService: builder, inspectService: dockerAuth, registryProxyService: proxyRegistry, buildConfig: buildConfig, persistenceService:persistenceService, inclusionService: Mock(ContainerInclusionService))
        def DOCKER = 'FROM foo'
        def user = new PlatformId(new User(id: 100))
        def cfg = new ContainerConfig()
        def req = new SubmitContainerTokenRequest(
                containerFile: encode(DOCKER),
                containerPlatform: 'arm64',
                containerConfig: cfg)

        when:
        def data = controller.makeRequestData(req, user, "")
        then:
        1 * proxyRegistry.getImageDigest(_) >> 'abc'
        1 * persistenceService.loadBuild(_,'abc')
        0 * builder.buildImage(_) >> null
        and:
        data.containerFile == DOCKER
        data.identity.userId == 100
        data.containerImage ==  'wave/build:be9ee6ac1eeff4b5'
        data.containerConfig == cfg
        data.platform.toString() == 'linux/arm64'
    }

    def 'should not run a build request when dry-run is specified' () {
        given:
        def builder = Mock(ContainerBuildService)
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def controller = new ContainerController(buildService: builder, inspectService: dockerAuth, registryProxyService: proxyRegistry, buildConfig:buildConfig, inclusionService: Mock(ContainerInclusionService))
        def DOCKER = 'FROM foo'
        def user = new PlatformId(new User(id: 100))
        def cfg = new ContainerConfig()
        def req = new SubmitContainerTokenRequest(
                containerFile: encode(DOCKER),
                containerPlatform: 'arm64',
                containerConfig: cfg,
                dryRun: true
        )

        when:
        def data = controller.makeRequestData(req, user, "")
        then:
        1 * proxyRegistry.getImageDigest(_) >> '123'
        0 * builder.buildImage(_) >> null
        and:
        data.containerFile == DOCKER
        data.identity.userId == 100
        data.containerImage ==  'wave/build:be9ee6ac1eeff4b5'
        data.containerConfig == cfg
        data.platform.toString() == 'linux/arm64'
    }

    def 'should make a mirror request' () {
        given:
        def mirrorService = applicationContext.getBean(ContainerMirrorService)
        def inspectService = Mock(ContainerInspectServiceImpl)
        def controller = new ContainerController(mirrorService: mirrorService, inspectService: inspectService, registryProxyService: proxyRegistry, buildConfig: buildConfig, inclusionService: Mock(ContainerInclusionService))
        def user = new PlatformId(new User(id: 100))
        def req = new SubmitContainerTokenRequest(
                containerImage: SOURCE,
                containerPlatform: 'arm64',
                buildRepository: BUILD,
                mirror: true
        )

        when:
        def data = controller.makeRequestData(req, user, "")
        then:
        1 * proxyRegistry.getImageDigest(SOURCE, user) >> 'sha256:12345'
        1 * proxyRegistry.getImageDigest(TARGET, user) >> null
        and:
        data.identity.userId == 100
        data.containerImage ==  TARGET
        data.platform.toString() == 'linux/arm64'
        data.buildId =~ /mr-.+/
        data.buildNew
        !data.freeze
        data.mirror

        where:
        SOURCE                          | BUILD           | TARGET
        'docker.io/source/image:latest' | 'quay.io'       | 'quay.io/source/image:latest'
        'docker.io/source/image:latest' | 'quay.io/lib'   | 'quay.io/lib/source/image:latest'
        'docker.io/source/image:latest' | 'quay.io/lib/'  | 'quay.io/lib/source/image:latest'
    }

    def 'should create build request' () {
        given:
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def controller = new ContainerController(inspectService: dockerAuth, buildConfig: buildConfig)

        when:
        def submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'))
        def build = controller.makeBuildRequest(submit, PlatformId.NULL,"")
        then:
        build.containerId =~ /7efaa2ed59c58a16/
        build.containerFile == 'FROM foo'
        build.targetImage == 'wave/build:7efaa2ed59c58a16'
        build.platform == ContainerPlatform.of('amd64')
        
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'amd64')
        build = controller.makeBuildRequest(submit, PlatformId.NULL, null)
        then:
        build.containerId =~ /7efaa2ed59c58a16/
        build.containerFile == 'FROM foo'
        build.targetImage == 'wave/build:7efaa2ed59c58a16'
        build.platform == ContainerPlatform.of('amd64')

        // using 'arm' platform changes the id
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, PlatformId.NULL, "")
        then:
        build.containerId =~ /be9ee6ac1eeff4b5/
        build.containerFile == 'FROM foo'
        build.targetImage == 'wave/build:be9ee6ac1eeff4b5'
        build.platform == ContainerPlatform.of('arm64')

        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), condaFile: encode('some::conda-recipe'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, PlatformId.NULL, "")
        then:
        build.containerId =~ /c6dac2e544419f71/
        build.containerFile == 'FROM foo'
        build.condaFile == 'some::conda-recipe'
        build.targetImage == 'wave/build:c6dac2e544419f71'
        build.platform == ContainerPlatform.of('arm64')

    }

    def 'should return a bad request exception when field is not encoded' () {
        given:
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def controller = new ContainerController(inspectService: dockerAuth, buildConfig: buildConfig)

        // validate containerFile
        when:
        controller.makeBuildRequest(
                new SubmitContainerTokenRequest(containerFile: 'FROM some:container'),
                Mock(PlatformId),
                null)
        then:
        def e = thrown(BadRequestException)
        e.message == "Invalid 'containerFile' attribute - make sure it encoded as a base64 string"

        // validate condaFile
        when:
        controller.makeBuildRequest(
                new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), condaFile: 'samtools=123'),
                Mock(PlatformId),
                null)
        then:
        e = thrown(BadRequestException)
        e.message == "Invalid 'condaFile' attribute - make sure it encoded as a base64 string"

    }

    def 'should add library prefix' () {
        when:
        def body = new SubmitContainerTokenRequest(containerImage: 'docker.io/hello-world')
        def req1 = HttpRequest.POST("/v1alpha2/container", body)
        def resp1 = client.toBlocking().exchange(req1, SubmitContainerTokenResponse)
        then:
        resp1.status() == HttpStatus.OK
        and:
        def token = resp1.body().containerToken
        and:
        token != null
        resp1.body().targetImage.contains("/wt/${token}/library/hello-world")
    }

    def 'should not add library prefix' () {
        when:
        def body = new SubmitContainerTokenRequest(containerImage: 'quay.io/hello-world')
        def req1 = HttpRequest.POST("/v1alpha2/container", body)
        def resp1 = client.toBlocking().exchange(req1, SubmitContainerTokenResponse)
        then:
        resp1.status() == HttpStatus.OK
        and:
        def token = resp1.body().containerToken
        and:
        token != null
        resp1.body().targetImage.contains("/wt/${token}/hello-world")
    }

    def 'should record a container request' () {
        when:
        def body = new SubmitContainerTokenRequest(containerImage: 'hello-world')
        def req1 = HttpRequest.POST("/v1alpha2/container", body)
        def resp1 = client.toBlocking().exchange(req1, SubmitContainerTokenResponse)
        then:
        resp1.status() == HttpStatus.OK
        and:
        def token = resp1.body().containerToken
        and:
        token != null
        resp1.body().targetImage.contains("/wt/${token}/library/hello-world")

        when:
        def req2 = HttpRequest.GET("/container-token/${token}")
        def resp2 = client.toBlocking().exchange(req2, DescribeWaveContainerResponse)
        then:
        resp2.status() == HttpStatus.OK
        and:
        def result = resp2.body()
        and:
        result.token == token
        result.request.containerImage == 'hello-world'
        result.source.image == 'docker.io/library/hello-world:latest'
        result.wave.image == resp1.body().targetImage
    }

    def 'should validate request' () {
        given:
        def validation = new ValidationServiceImpl()
        def pairing = Mock(PairingService)
        def channel = Mock(PairingChannel)
        def controller = new ContainerController(validationService: validation, pairingService: pairing, pairingChannel: channel)
        def err

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest())
        then:
        noExceptionThrown()

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(containerImage: 'foo:latest', towerAccessToken: '123'))
        then:
        noExceptionThrown()

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(containerImage: 'docker.io/foo:latest'))
        then:
        noExceptionThrown()

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(containerImage: 'http://docker.io/foo:latest'))
        then:
        err = thrown(BadRequestException)
        err.message == 'Invalid container repository name — offending value: http://docker.io/foo:latest'

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(containerImage: 'http:docker.io/foo:latest'))
        then:
        err = thrown(BadRequestException)
        err.message == 'Invalid container image name — offending value: http:docker.io/foo:latest'

    }

    def 'should validate mirror request' () {
        given:
        def pairing = Mock(PairingService)
        def channel = Mock(PairingChannel)
        def controller = new ContainerController(validationService: validationService, pairingService: pairing, pairingChannel: channel)
        def err

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'quay.io'), false)
        then:
        err = thrown(BadRequestException)
        err.message == 'Container mirroring requires the use of v2 API'

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'quay.io'), true)
        then:
        err = thrown(BadRequestException)
        err.message == 'Attribute `containerImage` is required when specifying `mirror` mode'

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'quay.io', containerImage: 'docker.io/foo'), true)
        then:
        err = thrown(BadRequestException)
        err.message == 'Container mirroring requires an authenticated request - specify the tower token attribute'

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'docker.io', containerImage: 'docker.io/foo', towerAccessToken: 'xyz'), true)
        then:
        err = thrown(BadRequestException)
        err.message == "Source and target mirror registry are the same - offending value 'docker.io'"

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'docker.io', containerImage: 'foo', towerAccessToken: 'xyz'), true)
        then:
        err = thrown(BadRequestException)
        err.message == "Source and target mirror registry are the same - offending value 'docker.io'"

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'quay.io', containerImage: 'docker.io/foo', towerAccessToken: 'xyz', containerFile: 'content'), true)
        then:
        err = thrown(BadRequestException)
        err.message == "Attribute `mirror` and `containerFile` conflict each other"

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'quay.io', containerImage: 'docker.io/foo', towerAccessToken: 'xyz', freeze: true), true)
        then:
        err = thrown(BadRequestException)
        err.message == "Attribute `mirror` and `freeze` conflict each other"

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'quay.io', containerImage: 'docker.io/foo', towerAccessToken: 'xyz', containerIncludes: ['include']), true)
        then:
        err = thrown(BadRequestException)
        err.message == "Attribute `mirror` and `containerIncludes` conflict each other"

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'quay.io', containerImage: 'docker.io/foo', towerAccessToken: 'xyz', containerConfig: new ContainerConfig(entrypoint: ['foo'])), true)
        then:
        err = thrown(BadRequestException)
        err.message == "Attribute `mirror` and `containerConfig` conflict each other"

        when:
        controller.validateMirrorRequest(new SubmitContainerTokenRequest(mirror: true, buildRepository: 'community.wave.seqera.io', containerImage: 'docker.io/foo', towerAccessToken: 'xyz'), true)
        then:
        err = thrown(BadRequestException)
        err.message == "Mirror registry not allowed - offending value 'community.wave.seqera.io'"
    }

    def 'should create response with conda packages' () {
        given:
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def freeze = new FreezeServiceImpl( inspectService: dockerAuth)
        def builder = Mock(ContainerBuildService)
        def proxyRegistry = Mock(RegistryProxyService)
        def addressResolver = Mock(HttpClientAddressResolver)
        def tokenService = Mock(ContainerRequestService)
        def persistence = Mock(PersistenceService)
        def controller = new ContainerController(freezeService:  freeze, buildService: builder, inspectService: dockerAuth,
                registryProxyService: proxyRegistry, buildConfig: buildConfig, inclusionService: Mock(ContainerInclusionService),
                addressResolver: addressResolver, containerService: tokenService, persistenceService: persistence, validationService: validationService, serverUrl: 'http://wave.com')

        when:'packages with conda'
        def CHANNELS = ['conda-forge', 'defaults']
        def CONDA_OPTS = new CondaOpts([basePackages: 'foo::one bar::two'])
        def PACKAGES = ['https://foo.com/lock.yml']
        def packagesSpec = new PackagesSpec(type: PackagesSpec.Type.CONDA, entries: PACKAGES, channels: CHANNELS, condaOpts: CONDA_OPTS)
        def req = new SubmitContainerTokenRequest(format: 'sif', packages: packagesSpec, freeze: true, buildRepository: 'docker.io/foo', towerAccessToken: '123')
        def user = new User(email: 'foo@bar.com', userName: 'foo')
        def id = PlatformId.of(user, req)
        def response = controller.handleRequest(null, req, id, true)

        then:
        1 * builder.buildImage(_) >> new BuildTrack('build123', 'oras://docker.io/foo:9b266d5b5c455fe0', true, true)
        and:
        1 * tokenService.computeToken(_) >> new TokenData('wavetoken123', Instant.now().plus(1, ChronoUnit.HOURS))
        and:
        response.status.code == 200
        verifyAll(response.body.get() as SubmitContainerTokenResponse) {
            targetImage == 'oras://docker.io/foo:9b266d5b5c455fe0'
            buildId == 'build123'
            containerToken == null
            cached == true
            succeeded == true
        }
    }

    def 'should throw BadRequestException when more than one artifact (container image, container file or packages) is provided in the request' () {
        given:
        def controller = new ContainerController(validationService: validationService, inclusionService: Mock(ContainerInclusionService), allowAnonymous: false)

        when: 'container access token is not provided'
        def req = new SubmitContainerTokenRequest(packages: new PackagesSpec())
        controller.handleRequest(null, req, null, true)
        then:
        def e = thrown(BadRequestException)
        e.message == "Missing user access token"

        when: 'container image  and container file and packages are provided'
        req = new SubmitContainerTokenRequest(containerFile: 'from foo', packages: new PackagesSpec())
        controller.handleRequest(null, req, new PlatformId(new User(id: 100)), true)
        then:
        e = thrown(BadRequestException)
        e.message == "Attribute `containerFile` and `packages` conflicts each other"

        when: 'packages are provided without v2'
        req = new SubmitContainerTokenRequest(packages: new PackagesSpec())
        controller.handleRequest(null, req, new PlatformId(new User(id: 100)), false)
        then:
        e = thrown(BadRequestException)
        e.message == "Attribute `packages` is not allowed"

        when:
        req = new SubmitContainerTokenRequest(containerFile: 'from foo', freeze: true)
        controller.handleRequest(null, req, new PlatformId(new User(id: 100)), false)
        then:
        e = thrown(BadRequestException)
        e.message == "Attribute `buildRepository` must be specified when using freeze mode [1]"

        when:
        req = new SubmitContainerTokenRequest(containerFile: 'from foo', freeze: true)
        controller.handleRequest(null, req, new PlatformId(new User(id: 100)), true)
        then:
        e = thrown(BadRequestException)
        e.message == "Attribute `buildRepository` must be specified when using freeze mode [1]"

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'alpine', freeze: true)
        controller.handleRequest(null, req, new PlatformId(new User(id: 100)), false)
        then:
        e = thrown(BadRequestException)
        e.message == "Attribute `buildRepository` must be specified when using freeze mode [2]"

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'alpine', freeze: true)
        controller.handleRequest(null, req, new PlatformId(new User(id: 100)), true)
        then:
        e = thrown(BadRequestException)
        e.message == "Attribute `buildRepository` must be specified when using freeze mode [2]"
    }

    @Unroll
    def 'should normalise community repo' () {
        given:
        def config = new BuildConfig(defaultPublicRepository: PUBLIC, reservedWords: ['build','library'] as Set)
        def controller = new ContainerController(buildConfig: config)
        expect:
        controller.targetRepo(REPO, STRATEGY) == EXPECTED
        
        where:
        REPO                | STRATEGY                      | PUBLIC        | EXPECTED
        'foo.com/alpha'     | null                          | 'foo.com'     | 'foo.com/alpha'
        'foo.com/alpha'     | ImageNameStrategy.imageSuffix | 'foo.com'     | 'foo.com/alpha'
        'foo.com/alpha'     | ImageNameStrategy.tagPrefix   | 'foo.com'     | 'foo.com/alpha'
        and:
        'foo.com/alpha/beta'| null                          | 'foo.com'     | 'foo.com/alpha/beta'
        'foo.com/alpha/beta'| ImageNameStrategy.imageSuffix | 'foo.com'     | 'foo.com/alpha/beta'
        'foo.com/alpha/beta'| ImageNameStrategy.tagPrefix   | 'foo.com'     | 'foo.com/alpha/beta'
        and:
        'foo.com'           | null                          | 'foo.com'     | 'foo.com/library'
        'foo.com'           | ImageNameStrategy.imageSuffix | 'foo.com'     | 'foo.com/library'
        'foo.com'           | ImageNameStrategy.tagPrefix   | 'foo.com'     | 'foo.com/library/build'
        and:
        'foo.com'           | null                          | null          | 'foo.com'
        'foo.com/alpha'     | null                          | null          | 'foo.com/alpha'
        'foo.com/alpha'     | ImageNameStrategy.imageSuffix | null          | 'foo.com/alpha'
        'foo.com/alpha'     | ImageNameStrategy.tagPrefix   | null          | 'foo.com/alpha'
        'foo.com/a/b/c'     | null                          | 'this.com'    | 'foo.com/a/b/c'

    }

    def 'should not allow reserved words' () {
        given:
        def config = new BuildConfig(defaultPublicRepository: 'foo.com',  reservedWords: ['build','library'] as Set)
        def controller = new ContainerController(buildConfig: config)

        when:
        controller.targetRepo('foo.com/library', null)
        then:
        def e = thrown(BadRequestException)
        e.message == "Use of repository 'foo.com/library' is not allowed"

        when:
        controller.targetRepo('foo.com/build', null)
        then:
        e = thrown(BadRequestException)
        e.message == "Use of repository 'foo.com/build' is not allowed"

        when:
        controller.targetRepo('foo.com/bar/build', null)
        then:
        e = thrown(BadRequestException)
        e.message == "Use of repository 'foo.com/bar/build' is not allowed"

        when:
        controller.targetRepo('foo.com/ok', null)
        then:
        noExceptionThrown()

        when:
        controller.targetRepo('bar.com/build', null)
        then:
        noExceptionThrown()
    }

    def 'should return the container record' () {
        given:
        def body = new SubmitContainerTokenRequest(containerImage: 'hello-world')
        def req1 = HttpRequest.POST("/v1alpha2/container", body)
        def resp1 = client.toBlocking().exchange(req1, SubmitContainerTokenResponse)
        and:
        resp1.status() == HttpStatus.OK
        and:
        def requestId = resp1.body().requestId

        when:
        def req2 = HttpRequest.GET("/v1alpha2/container/${requestId}")
        def resp2 = client.toBlocking().exchange(req2, WaveContainerRecord)
        then:
        resp2.status() == HttpStatus.OK
        and:
        def result = resp2.body()
        and:
        result.containerImage == 'hello-world'
        result.sourceImage == 'docker.io/library/hello-world:latest'
        result.waveImage == resp1.body().targetImage
    }

    def 'should return the container status' () {
        given:
        def body = new SubmitContainerTokenRequest(containerImage: 'hello-world')
        def req1 = HttpRequest.POST("/v1alpha2/container", body)
        def resp1 = client.toBlocking().exchange(req1, SubmitContainerTokenResponse)
        and:
        resp1.status() == HttpStatus.OK
        and:
        def requestId = resp1.body().requestId

        when:
        def req2 = HttpRequest.GET("/v1alpha2/container/${requestId}/status")
        def resp2 = client.toBlocking().exchange(req2, ContainerStatusResponse)
        then:
        resp2.status() == HttpStatus.OK
        and:
        ContainerStatusResponse result = resp2.body()
        and:
        result.id == requestId
        result.succeeded
        result.creationTime
        result.duration
    }
}
