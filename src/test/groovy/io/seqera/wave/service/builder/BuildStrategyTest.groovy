package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.api.BuildContext
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildStrategyTest extends Specification {

    def 'should get kaniko command' () {
        given:
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, BuildFormat.DOCKER, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/c168dba125e28777/Containerfile',
                '--context',
                '/work/foo/c168dba125e28777/context',
                '--destination',
                'quay.io/wave:c168dba125e28777',
                '--cache=true',
                '--custom-platform',
                'linux/amd64',
                '--cache-repo',
                'reg.io/wave/build/cache',
        ]
    }

    def 'should get kaniko command with build context' () {
        given:
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        def build = Mock(BuildContext) {tarDigest >> '123'}
        and:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, BuildFormat.DOCKER, Mock(User), null, build, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/3980470531b4a52a/Containerfile',
                '--context',
                '/work/foo/3980470531b4a52a/context',
                '--destination',
                'quay.io/wave:3980470531b4a52a',
                '--cache=true',
                '--custom-platform',
                'linux/amd64',
                '--cache-repo',
                'reg.io/wave/build/cache',
        ]
    }

    def 'should get singularity command' () {
        given:
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, BuildFormat.SINGULARITY, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                "sh",
                "-c",
                "singularity build -B /work/foo/c168dba125e28777/context:/work/foo/c168dba125e28777/context image.sif /work/foo/c168dba125e28777/Containerfile && singularity push image.sif oras://quay.io/wave:c168dba125e28777"
            ]
    }

}
