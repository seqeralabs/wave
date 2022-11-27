package io.seqera.wave.controller

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.auth.DockerAuthService
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerTokenControllerTest extends Specification {

    def 'should create request data' () {
        given:
        def controller = new ContainerTokenController()

        when:
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest')
        def data = controller.makeRequestData(req, null, "")
        then:
        data.containerImage == 'ubuntu:latest'

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        data = controller.makeRequestData(req, new User(id: 100), "127.0.0.1")
        then:
        data.containerImage == 'ubuntu:latest'
        data.userId == 100
        data.workspaceId == 10 
        data.containerConfig == cfg
        data.platform == ContainerPlatform.of('arm64')


        when:
        req = new SubmitContainerTokenRequest()
        controller.makeRequestData(req, new User(id: 100),"")
        then:
        thrown(BadRequestException)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'ubuntu', containerFile: 'from foo')
        controller.makeRequestData(req, new User(id: 100),"")
        then:
        thrown(BadRequestException)

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
        def dockerAuth = Mock(DockerAuthService)
        def proxyRegistry = Mock(RegistryProxyService)
        def controller = new ContainerTokenController(buildService: builder, dockerAuthService: dockerAuth, registryProxyService: proxyRegistry,
                workspace: Path.of('/some/wsp'), defaultBuildRepo: 'wave/build', defaultCacheRepo: 'wave/cache')
        def DOCKER = 'FROM foo'
        def user = new User(id: 100)
        def cfg = new ContainerConfig()
        def req = new SubmitContainerTokenRequest(
                containerFile: encode(DOCKER),
                containerPlatform: 'arm64',
                containerConfig: cfg)

        when:
        def data = controller.makeRequestData(req, user, "")
        then:
        1 * proxyRegistry.isManifestPresent(_) >> false
        1 * builder.buildImage(_) >> null
        and:
        data.containerFile == 'FROM foo'
        data.userId == 100
        data.containerImage ==  'wave/build:7d6b54efe23408c0938290a9ae49cf21'
        data.containerConfig == cfg
        data.platform.toString() == 'linux/arm64'
    }

    def 'should not run a build request if manifest is present' () {
        given:
        def builder = Mock(ContainerBuildService)
        def dockerAuth = Mock(DockerAuthService)
        def proxyRegistry = Mock(RegistryProxyService)
        def controller = new ContainerTokenController(buildService: builder, dockerAuthService: dockerAuth, registryProxyService: proxyRegistry,
                workspace: Path.of('/some/wsp'), defaultBuildRepo: 'wave/build', defaultCacheRepo: 'wave/cache')
        def DOCKER = 'FROM foo'
        def user = new User(id: 100)
        def cfg = new ContainerConfig()
        def req = new SubmitContainerTokenRequest(
                containerFile: encode(DOCKER),
                containerPlatform: 'arm64',
                containerConfig: cfg)

        when:
        def data = controller.makeRequestData(req, user, "")
        then:
        1 * proxyRegistry.isManifestPresent(_) >> true
        0 * builder.buildImage(_) >> null
        and:
        data.containerFile == 'FROM foo'
        data.userId == 100
        data.containerImage ==  'wave/build:7d6b54efe23408c0938290a9ae49cf21'
        data.containerConfig == cfg
        data.platform.toString() == 'linux/arm64'
    }

    def 'should create build request' () {
        given:
        def dockerAuth = Mock(DockerAuthService)
        def controller = new ContainerTokenController(dockerAuthService: dockerAuth, workspace: Path.of('/some/wsp'), defaultBuildRepo: 'wave/build', defaultCacheRepo: 'wave/cache')

        when:
        def submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'))
        def build = controller.makeBuildRequest(submit, null,"")
        then:
        build.id == '21159a79b614be796103c7b752fdfbf0'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:21159a79b614be796103c7b752fdfbf0'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('amd64')
        
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'amd64')
        build = controller.makeBuildRequest(submit, null, null)
        then:
        build.id == '21159a79b614be796103c7b752fdfbf0'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:21159a79b614be796103c7b752fdfbf0'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('amd64')

        // using 'arm' platform changes the id
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, null, "")
        then:
        build.id == '7d6b54efe23408c0938290a9ae49cf21'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:7d6b54efe23408c0938290a9ae49cf21'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('arm64')

        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), condaFile: encode('some::conda-recipe'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, null, "")
        then:
        build.id == '0c7eebc2fdbfd514ff4d80c28d08dff8'
        build.dockerFile == 'FROM foo'
        build.condaFile == 'some::conda-recipe'
        build.targetImage == 'wave/build:0c7eebc2fdbfd514ff4d80c28d08dff8'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('arm64')
    }
}
