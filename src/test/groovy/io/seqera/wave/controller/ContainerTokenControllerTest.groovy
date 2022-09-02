package io.seqera.wave.controller

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.SubmitContainerTokenRequest
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
        data.containerPlatform == 'arm64'


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
        data.containerPlatform == 'arm64'
    }

    def 'should create build request' () {
        given:
        def controller = new ContainerTokenController(workspace: Path.of('/some/wsp'), buildRepo: 'wave/build')

        when:
        def submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'))
        def build = controller.makeBuildRequest(submit, null)
        then:
        build.id == '15c52fa7417693a1173aa0d5cdb83076'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:15c52fa7417693a1173aa0d5cdb83076'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.containerPlatform == 'amd64'
        
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'amd64')
        build = controller.makeBuildRequest(submit, null)
        then:
        build.id == '15c52fa7417693a1173aa0d5cdb83076'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:15c52fa7417693a1173aa0d5cdb83076'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.containerPlatform == 'amd64'

        // using 'arm' platform changes the id
        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, null)
        then:
        build.id == '6a3f47e8e841c938ad3383e4dc555384'
        build.dockerFile == 'FROM foo'
        build.targetImage == 'wave/build:6a3f47e8e841c938ad3383e4dc555384'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.containerPlatform == 'arm64'

        when:
        submit = new SubmitContainerTokenRequest(containerFile: encode('FROM foo'), condaFile: encode('some::conda-recipe'), containerPlatform: 'arm64')
        build = controller.makeBuildRequest(submit, null)
        then:
        build.id == '4a51e1463584269e32b30c27b6bc1467'
        build.dockerFile == 'FROM foo'
        build.condaFile == 'some::conda-recipe'
        build.targetImage == 'wave/build:4a51e1463584269e32b30c27b6bc1467'
        build.workDir == Path.of('/some/wsp').resolve(build.id)
        build.containerPlatform == 'arm64'
    }
}
