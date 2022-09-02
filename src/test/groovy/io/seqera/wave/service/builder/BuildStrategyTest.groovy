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
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, Mock(User), 'amd64')

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/4769bf5c5b2453ff8677b9b2c7b2a375/Dockerfile',
                '--context',
                '/work/foo/4769bf5c5b2453ff8677b9b2c7b2a375',
                '--destination',
                'quay.io/wave:4769bf5c5b2453ff8677b9b2c7b2a375',
                '--cache=true',
                '--cache-repo',
                'reg.io/wave/build/cache'
        ]
    }

}
