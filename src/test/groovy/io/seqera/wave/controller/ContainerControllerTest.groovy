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

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ImageNameStrategy
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.config.SpackOpts
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
import io.seqera.wave.service.pairing.PairingRecord
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.token.ContainerTokenService
import io.seqera.wave.service.token.TokenData
import io.seqera.wave.service.validation.ValidationServiceImpl
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
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

    def 'should create request data' () {
        given:
        def controller = new ContainerController(inclusionService: Mock(ContainerInclusionService))

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
        and:
        def controller = Spy(new ContainerController(freezeService: freeze, inclusionService: Mock(ContainerInclusionService)))
        and:
        def target = 'docker.io/repo/ubuntu:latest'
        def BUILD = Mock(BuildRequest) {
            getTargetImage() >> target
        }
        and:
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true, buildRepository: 'docker.io/foo/bar')

        when:
        def data = controller.makeRequestData(req, PlatformId.NULL, "")
        then:
        1 * freeze.freezeBuildRequest(req, _) >> req.copyWith(containerFile: 'FROM ubuntu:latest')
        1 * controller.makeBuildRequest(_,_,_) >> BUILD
        1 * controller.checkBuild(BUILD,false) >> new BuildTrack('1', target, false)
        and:
        data.containerImage == target

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
        def proxyRegistry = Mock(RegistryProxyService)
        def controller = new ContainerController(buildService: builder, dockerAuthService: dockerAuth, registryProxyService: proxyRegistry, buildConfig: buildConfig, inclusionService: Mock(ContainerInclusionService))
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
        1 * builder.buildImage(_) >> new BuildTrack('1', 'wave/build:be9ee6ac1eeff4b5')
        and:
        data.containerFile == DOCKER
        data.identity.userId == 100
        data.containerImage ==  'wave/build:be9ee6ac1eeff4b5'
        data.containerConfig == cfg
        data.platform.toString() == 'linux/arm64'
    }

    def 'should not run a build request if manifest is present' () {
        given:
        def builder = Mock(ContainerBuildService)
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def proxyRegistry = Mock(RegistryProxyService)
        def persistenceService = Mock(PersistenceService)
        def controller = new ContainerController(buildService: builder, dockerAuthService: dockerAuth, registryProxyService: proxyRegistry, buildConfig: buildConfig, persistenceService:persistenceService, inclusionService: Mock(ContainerInclusionService))
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
        def proxyRegistry = Mock(RegistryProxyService)
        def controller = new ContainerController(buildService: builder, dockerAuthService: dockerAuth, registryProxyService: proxyRegistry, buildConfig:buildConfig, inclusionService: Mock(ContainerInclusionService))
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

    def 'should create build request' () {
        given:
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def controller = new ContainerController(dockerAuthService: dockerAuth, buildConfig: buildConfig)

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

        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), spackFile: encode('some::spack-recipe'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, PlatformId.NULL, "")
        then:
        build.containerId =~ /b7d730d274d1e057/
        build.containerFile.endsWith('\nFROM foo')
        build.containerFile.startsWith('# Builder image\n')
        build.condaFile == null
        build.spackFile == 'some::spack-recipe'
        build.targetImage == 'wave/build:b7d730d274d1e057'
        build.platform == ContainerPlatform.of('arm64')
    }

    def 'should return a bad request exception when field is not encoded' () {
        given:
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def controller = new ContainerController(dockerAuthService: dockerAuth, buildConfig: buildConfig)

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

        // validate spackFile
        when:
        controller.makeBuildRequest(
                new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), spackFile: 'spack@123'),
                Mock(PlatformId),
                null)
        then:
        e = thrown(BadRequestException)
        e.message == "Invalid 'spackFile' attribute - make sure it encoded as a base64 string"

    }

    def 'should add library prefix' () {
        when:
        def body = new SubmitContainerTokenRequest(containerImage: 'docker.io/hello-world')
        def req1 = HttpRequest.POST("/container-token", body)
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
        def req1 = HttpRequest.POST("/container-token", body)
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
        def req1 = HttpRequest.POST("/container-token", body)
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
        def msg

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest())
        then:
        noExceptionThrown()

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(towerEndpoint: 'http://foo.com', towerAccessToken: '123'))
        then:
        1 * pairing.getPairingRecord('tower','http://foo.com') >> Mock(PairingRecord)
        and:
        noExceptionThrown()

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(towerEndpoint: 'https://foo.com', towerAccessToken: '123'))
        then:
        1 * pairing.getPairingRecord('tower','https://foo.com') >> Mock(PairingRecord)
        and:
        noExceptionThrown()

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(towerEndpoint: 'https://tower.something.com/api', towerAccessToken: '123'))
        then:
        1 * pairing.getPairingRecord('tower','https://tower.something.com/api') >> Mock(PairingRecord)
        and:
        noExceptionThrown()

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(towerEndpoint: 'https://tower.something.com/api', towerAccessToken: '123'))
        then:
        1 * pairing.getPairingRecord('tower','https://tower.something.com/api') >> null
        and:
        msg = thrown(BadRequestException)
        msg.message == "Missing pairing record for Tower endpoint 'https://tower.something.com/api'"

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
        msg = thrown(BadRequestException)
        msg.message == 'Invalid container repository name — offending value: http://docker.io/foo:latest'

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(containerImage: 'http:docker.io/foo:latest'))
        then:
        msg = thrown(BadRequestException)
        msg.message == 'Invalid container image name — offending value: http:docker.io/foo:latest'

    }

    def 'should allow any registered endpoint' () {
        given:
        def registeredUri = 'http://foo.com'
        def validation = new ValidationServiceImpl()
        def pairing = Mock(PairingService)
        def channel = Mock(PairingChannel)
        def controller = new ContainerController(validationService: validation, pairingService: pairing, pairingChannel: channel)

        when:
        controller.validateContainerRequest(new SubmitContainerTokenRequest(towerEndpoint: registeredUri, towerAccessToken: '123'))
        then:
        1 * pairing.getPairingRecord('tower',registeredUri) >> Mock(PairingRecord)
        and:
        noExceptionThrown()

    }

    def 'should create response with conda packages' () {
        given:
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def freeze = new FreezeServiceImpl( inspectService: dockerAuth)
        def builder = Mock(ContainerBuildService)
        def proxyRegistry = Mock(RegistryProxyService)
        def addressResolver = Mock(HttpClientAddressResolver)
        def tokenService = Mock(ContainerTokenService)
        def persistence = Mock(PersistenceService)
        def controller = new ContainerController(freezeService:  freeze, buildService: builder, dockerAuthService: dockerAuth,
                registryProxyService: proxyRegistry, buildConfig: buildConfig, inclusionService: Mock(ContainerInclusionService),
                addressResolver: addressResolver, tokenService: tokenService, persistenceService: persistence, serverUrl: 'http://wave.com')

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
        1 * builder.buildImage(_) >> new BuildTrack('build123', 'oras://docker.io/foo:9b266d5b5c455fe0', true)
        and:
        1 * tokenService.computeToken(_) >> new TokenData('wavetoken123', Instant.now().plus(1, ChronoUnit.HOURS))
        and:
        response.status.code == 200
        verifyAll(response.body.get() as SubmitContainerTokenResponse) {
            targetImage == 'oras://docker.io/foo:9b266d5b5c455fe0'
            buildId == 'build123'
            containerToken == null
            cached == true
        }
    }

    def 'should create response with spack packages' () {
        given:
        def dockerAuth = Mock(ContainerInspectServiceImpl)
        def freeze = new FreezeServiceImpl( inspectService: dockerAuth)
        def builder = Mock(ContainerBuildService)
        def proxyRegistry = Mock(RegistryProxyService)
        def addressResolver = Mock(HttpClientAddressResolver)
        def tokenService = Mock(ContainerTokenService)
        def persistence = Mock(PersistenceService)
        def controller = new ContainerController(freezeService:  freeze, buildService: builder, dockerAuthService: dockerAuth,
                registryProxyService: proxyRegistry, buildConfig: buildConfig, inclusionService: Mock(ContainerInclusionService),
                addressResolver: addressResolver, tokenService: tokenService, persistenceService: persistence, serverUrl: 'https://wave.seqera.io')

        when:'packages with spack'
        def SPACK_OPTS = new SpackOpts([
                basePackages: 'foo bar',
                commands: ['run','--this','--that']
        ])
        def packages = new PackagesSpec(type: PackagesSpec.Type.SPACK, spackOpts: SPACK_OPTS)
        def req = new SubmitContainerTokenRequest(format: 'docker', packages: packages)
        def response = controller.handleRequest(null, req, new PlatformId(new User(id: 100), 10), true)

        then:
        1 * builder.buildImage(_) >> new BuildTrack('build123', 'foo:1234', true)
        and:
        1 * tokenService.computeToken(_) >> new TokenData('wavetoken123', Instant.now().plus(1, ChronoUnit.HOURS))
        and:
        response.status.code == 200
        verifyAll(response.body.get() as SubmitContainerTokenResponse) {
            targetImage == 'wave.seqera.io/wt/wavetoken123/library/foo:1234'
            buildId == 'build123'
            containerToken == 'wavetoken123'
            cached == true
        }
    }

    def 'should throw BadRequestException when more than one artifact (container image, container file or packages) is provided in the request' () {
        given:
        def controller = new ContainerController(inclusionService: Mock(ContainerInclusionService), allowAnonymous: false)

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
    }

    @Unroll
    def 'should normalise community repo' () {
        given:
        def config = new BuildConfig(reservedWords: ['build','library'] as Set)
        def controller = new ContainerController(buildConfig: config)
        expect:
        controller.communityRepo(REPO, STRATEGY) == EXPECTED
        
        where:
        REPO                | STRATEGY                      | EXPECTED
        'foo.com/alpha'     | null                          | 'foo.com/alpha'
        'foo.com/alpha'     | ImageNameStrategy.imageSuffix | 'foo.com/alpha'
        'foo.com/alpha'     | ImageNameStrategy.tagPrefix   | 'foo.com/alpha'
        and:
        'foo.com/alpha/beta'| null                          | 'foo.com/alpha/beta'
        'foo.com/alpha/beta'| ImageNameStrategy.imageSuffix | 'foo.com/alpha/beta'
        'foo.com/alpha/beta'| ImageNameStrategy.tagPrefix   | 'foo.com/alpha/beta'
        and:
        'foo.com'           | null                          | 'foo.com/library'
        'foo.com'           | ImageNameStrategy.imageSuffix | 'foo.com/library'
        'foo.com'           | ImageNameStrategy.tagPrefix   | 'foo.com/library/build'

    }

    def 'should not allow reserved words' () {
        given:
        def config = new BuildConfig(reservedWords: ['build','library'] as Set)
        def controller = new ContainerController(buildConfig: config)

        when:
        controller.communityRepo('foo.com/library', null)
        then:
        def e = thrown(BadRequestException)
        e.message == "Use of repository 'foo.com/library' is not allowed"

        when:
        controller.communityRepo('foo.com/build', null)
        then:
        e = thrown(BadRequestException)
        e.message == "Use of repository 'foo.com/build' is not allowed"

        when:
        controller.communityRepo('foo.com/bar/build', null)
        then:
        e = thrown(BadRequestException)
        e.message == "Use of repository 'foo.com/bar/build' is not allowed"

        when:
        controller.communityRepo('foo.com/ok', null)
        then:
        noExceptionThrown()
    }
}
