package io.seqera.wave.controller

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
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
        def data = controller.makeRequestData(req, null)
        then:
        data.containerImage == 'ubuntu:latest'

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')
        data = controller.makeRequestData(req, new User(id: 100))
        then:
        data.containerImage == 'ubuntu:latest'
        data.userId == 100
        data.workspaceId == 10 
        data.containerConfig == cfg
        data.platform == ContainerPlatform.of('arm64')


        when:
        req = new SubmitContainerTokenRequest()
        controller.makeRequestData(req, new User(id: 100))
        then:
        thrown(BadRequestException)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'ubuntu', containerFile: 'from foo')
        controller.makeRequestData(req, new User(id: 100))
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
        def controller = new ContainerTokenController(buildService: builder, workspace: Path.of('/some/wsp'), buildRepo: 'wave/build')
        def DOCKER = 'FROM foo'
        def user = new User(id: 100)
        def cfg = new ContainerConfig()
        def req = new SubmitContainerTokenRequest(
                containerFile: encode(DOCKER),
                containerPlatform: 'arm64',
                containerConfig: cfg)

        when:
        def data = controller.makeRequestData(req, user)
        then:
        1 * builder.buildImage(_) >> 'some/repo:xyz'
        and:
        data.containerFile == 'FROM foo'
        data.userId == 100
        data.containerImage ==  'some/repo:xyz'
        data.containerConfig == cfg
        data.platform.toString() == 'linux/arm64/v8'
    }

    def 'should create build request' () {
        given:
        def controller = new ContainerTokenController(workspace: Path.of('/some/wsp'), buildRepo: 'wave/build')

        when:
        def submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'))
        def build = controller.makeBuildRequest(submit, null)
        then:
        build.id == 'acc83dc6d094823894869bf3cf3de17b'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:acc83dc6d094823894869bf3cf3de17b'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('amd64')
        
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'amd64')
        build = controller.makeBuildRequest(submit, null)
        then:
        build.id == 'acc83dc6d094823894869bf3cf3de17b'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:acc83dc6d094823894869bf3cf3de17b'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('amd64')

        // using 'arm' platform changes the id
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, null)
        then:
        build.id == 'b3692c4aa2a61e93e4ddde5491477eed'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:b3692c4aa2a61e93e4ddde5491477eed'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('arm64')

        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), condaFile: encode('some::conda-recipe'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, null)
        then:
        build.id == 'c4e97d10f83ab8af1b58f4941cc51ceb'
        build.dockerFile == 'FROM foo'
        build.condaFile == 'some::conda-recipe'
        build.targetImage == 'wave/build:c4e97d10f83ab8af1b58f4941cc51ceb'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.platform == ContainerPlatform.of('arm64')
    }
}
