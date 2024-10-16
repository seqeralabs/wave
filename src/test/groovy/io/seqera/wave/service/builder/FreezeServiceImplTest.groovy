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

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.inspect.ContainerInspectServiceImpl
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class FreezeServiceImplTest extends Specification  {

    @Inject
    FreezeServiceImpl freezeService

    @Inject
    ContainerInspectService authService

    @MockBean(ContainerInspectServiceImpl)
    ContainerInspectService authService() {
        Mock(ContainerInspectService)
    }

    def 'should add container config to dockerfile' () {

        when:
        def config = new ContainerConfig()
        def result = FreezeServiceImpl.appendConfigToContainerFile('FROM foo', new SubmitContainerTokenRequest(containerConfig: config))
        then:
        result == 'FROM foo'

        when:
        def layers = [
                new ContainerLayer('https://some.host', '012abc'),
                new ContainerLayer('data:fafafa', '000aaa'),
                new ContainerLayer('data:xyz', '999fff')]
        config = new ContainerConfig(
                workingDir: '/some/work/dir',
                env: ['FOO=one', 'BAR=two'],
                cmd:['/this','--that'],
                entrypoint: ['/my','--entry'],
                layers: layers)
        result = FreezeServiceImpl.appendConfigToContainerFile('FROM foo', new SubmitContainerTokenRequest(containerConfig: config))
        then:
        result == '''\
                FROM foo
                ADD layer-012abc.tar.gz /
                ADD layer-000aaa.tar.gz /
                ADD layer-999fff.tar.gz /
                WORKDIR /some/work/dir
                ENV FOO=one BAR=two
                ENTRYPOINT ["/my", "--entry"]
                CMD ["/this", "--that"]
                '''.stripIndent()
    }

    def 'should create build file given a container image' () {
        when:
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true)
        def result = freezeService.createBuildFile(req, Mock(PlatformId))
        then:
        result == '''\
            # wave generated container file
            FROM ubuntu:latest
            '''.stripIndent(true)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2']))
        result = freezeService.createBuildFile(req, PlatformId.NULL)
        then:
        result == '''\
            # wave generated container file
            FROM ubuntu:latest
            ENV FOO=1 BAR=2
            '''.stripIndent(true)
    }

    def 'should create build file given a container image for singularity' () {
        when:
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true, format: 'sif')
        def result = freezeService.createBuildFile(req, Mock(PlatformId))
        then:
        result == '''\
            # wave generated container file
            BootStrap: docker
            From: ubuntu:latest
            '''.stripIndent(true)
    }

    def 'should create build file given a container image and config for singularity ' () {
        given:
        def l1 = new ContainerLayer('/some/loc', 'digest1')
        def l2 = new ContainerLayer('/other/loc', 'digest2')
        def config = new ContainerConfig(env:['FOO=1', 'BAR=2'], entrypoint: ['bash', '--this', '--that'], layers: [l1, l2])
        def req = new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', freeze: true, format: 'sif', containerConfig: config)
        when:
        def result = freezeService.createBuildFile(req, Mock(PlatformId))
        then:
        result == '''\
            # wave generated container file
            BootStrap: docker
            From: ubuntu:latest
            %files
              {{wave_context_dir}}/layer-digest1/* /
              {{wave_context_dir}}/layer-digest2/* /
            %environment
              export FOO=1 BAR=2
            %runscript
              bash --this --that
            '''.stripIndent(true)

    }
    def 'should create build file given a container file' () {
        given:
        def ENCODED = 'FROM foo\nRUN this\n'.bytes.encodeBase64().toString()

        when:
        def req = new SubmitContainerTokenRequest(containerFile: ENCODED, freeze: true)
        def result = freezeService.createBuildFile(req, Mock(PlatformId))
        then:
        // nothing to do here =>  returns null
        result == null

        when:
        req = new SubmitContainerTokenRequest(containerFile: ENCODED, freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2'], workingDir: '/work/dir'))
        result = freezeService.createBuildFile(req, Mock(PlatformId))
        then:
        // nothing to do here =>  returns null
        result == '''\
            FROM foo
            RUN this
             
            # wave generated container file
            WORKDIR /work/dir
            ENV FOO=1 BAR=2
            '''.stripIndent()
    }

    def 'should throw an error' () {
        when:
        def req = new SubmitContainerTokenRequest(containerFile: 'FROM foo\nRUN this\n', freeze: false)
        freezeService.createBuildFile(req, Mock(PlatformId))
        then:
        thrown(AssertionError)
    }

    def 'should create build request given a container image' () {
        when:
        def req = new SubmitContainerTokenRequest(containerImage: 'hello-world:latest', freeze: true)
        def result = freezeService.freezeBuildRequest(req, Mock(PlatformId))
        then:
        1* authService.containerEntrypoint(_,_,_) >> null
        and:
        new String(result.containerFile.decodeBase64()) == '''\
            # wave generated container file
            FROM hello-world:latest
            '''.stripIndent(true)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'hello-world:latest', freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2']))
        result = freezeService.freezeBuildRequest(req, Mock(PlatformId))
        then:
        1* authService.containerEntrypoint(_,_,_) >> null
        and:
        new String(result.containerFile.decodeBase64()) == '''\
            # wave generated container file
            FROM hello-world:latest
            ENV FOO=1 BAR=2
            '''.stripIndent(true)

        when:
        req = new SubmitContainerTokenRequest(containerImage: 'hello-world:latest', freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2']))
        result = freezeService.freezeBuildRequest(req, Mock(PlatformId))
        then:
        1* authService.containerEntrypoint(_,_,_) >> ['/foo/entry.sh']
        and:
        new String(result.containerFile.decodeBase64()) == '''\
            # wave generated container file
            FROM hello-world:latest
            ENV WAVE_ENTRY_CHAIN="/foo/entry.sh"
            ENV FOO=1 BAR=2
            '''.stripIndent(true)
    }

    def 'should create build request' () {
        given:
        def ENCODED = 'FROM foo\nRUN this\n'.bytes.encodeBase64().toString()

        // 1. no container config is provided
        // therefore the container file is not changed
        when:
        def req = new SubmitContainerTokenRequest(containerFile: ENCODED, freeze: true)
        def result = freezeService.freezeBuildRequest(req, Mock(PlatformId))
        then:
        0* authService.containerEntrypoint(_,_,_) >> null
        and:
        result.containerFile == req.containerFile

        // 2. a container config is provided
        // the container file is updated correspondingly
        when:
        req = new SubmitContainerTokenRequest(containerFile: ENCODED, freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2'], workingDir: '/work/dir'))
        result = freezeService.freezeBuildRequest(req, Mock(PlatformId))
        then:
        1* authService.containerEntrypoint(_,_,_) >> null
        and:
        // nothing to do here =>  returns null
        new String(result.containerFile.decodeBase64()) == '''\
            FROM foo
            RUN this
             
            # wave generated container file
            WORKDIR /work/dir
            ENV FOO=1 BAR=2
            '''.stripIndent()

        // 3. the container image specifies an entrypoint
        // therefore the 'WAVE_ENTRY_CHAIN' is added to the resulting container file
        when:
        req = new SubmitContainerTokenRequest(containerFile: ENCODED, freeze: true, containerConfig: new ContainerConfig(env:['FOO=1', 'BAR=2'], workingDir: '/work/dir'))
        result = freezeService.freezeBuildRequest(req, Mock(PlatformId))
        then:
        1 * authService.containerEntrypoint(_,_,_) >> ['/some/entry.sh']
        and:
        // nothing to do here =>  returns null
        new String(result.containerFile.decodeBase64()) == '''\
            FROM foo
            RUN this
             
            # wave generated container file
            ENV WAVE_ENTRY_CHAIN="/some/entry.sh"
            WORKDIR /work/dir
            ENV FOO=1 BAR=2
            '''.stripIndent()
    }

    def 'should create containerfile' () {
        when:
        def result = FreezeServiceImpl.createContainerFile(new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest'))
        then:
        result == '''\
            FROM ubuntu:latest
            '''.stripIndent()


        when:
        result = FreezeServiceImpl.createContainerFile(new SubmitContainerTokenRequest(containerImage: 'ubuntu:latest', format: 'sif'))
        then:
        result == '''\
            BootStrap: docker
            From: ubuntu:latest
            '''.stripIndent()
    }
}
