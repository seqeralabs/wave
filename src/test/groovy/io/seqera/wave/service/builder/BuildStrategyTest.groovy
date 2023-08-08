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
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, Mock(User), null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/40e8a6dba50e9b3b609a19c12420d3eb/Dockerfile',
                '--destination',
                'quay.io/wave:40e8a6dba50e9b3b609a19c12420d3eb',
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
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, null, Mock(User), null, build, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/810cd2279583f9fbed652c4c1530b7e2/Dockerfile',
                '--destination',
                'quay.io/wave:810cd2279583f9fbed652c4c1530b7e2',
                '--cache=true',
                '--custom-platform',
                'linux/amd64',
                '--context',
                'tar:///work/foo/810cd2279583f9fbed652c4c1530b7e2/context.tar.gz',
                '--cache-repo',
                'reg.io/wave/build/cache',
        ]
    }

}
