package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

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
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, Mock(User))

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '/kaniko/executor',
                '--dockerfile',
                '/work/foo/ef16568120a7a0bd0c679942942ea8e8/Dockerfile',
                '--context',
                '/work/foo/ef16568120a7a0bd0c679942942ea8e8',
                '--destination',
                'quay.io/wave:ef16568120a7a0bd0c679942942ea8e8',
                '--cache=true',
                '--cache-repo',
                'reg.io/wave/build/cache'
        ]
    }

}
