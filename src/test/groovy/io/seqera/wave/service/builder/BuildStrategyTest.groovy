package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildStrategyTest extends Specification {

    def 'should get kaniko command' () {
        given:
        def service = Spy(BuildStrategy)
        service.cacheRepo = 'reg.io/wave/build/cache'
        and:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, Mock(User), ContainerPlatform.of('amd64'))

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/371f47bac77d67d55d29e0c111c508ef/Dockerfile',
                '--context',
                '/work/foo/371f47bac77d67d55d29e0c111c508ef',
                '--destination',
                'quay.io/wave:371f47bac77d67d55d29e0c111c508ef',
                '--cache=true',
                '--cache-repo',
                'reg.io/wave/build/cache'
        ]
    }

}
