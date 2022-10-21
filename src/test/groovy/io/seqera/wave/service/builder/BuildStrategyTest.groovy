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
        def cache = 'reg.io/wave/build/cache'
        def service = Spy(BuildStrategy)
        and:
        def work = Path.of('/work/foo')
        def REQ = new BuildRequest('from foo', work, 'quay.io/wave', null, Mock(User), ContainerPlatform.of('amd64'),'{auth}', cache, "")

        when:
        def cmd = service.launchCmd(REQ)
        then:
        cmd == [
                '--dockerfile',
                '/work/foo/2f6d45fae801bd000d175c58148354cc/Dockerfile',
                '--context',
                '/work/foo/2f6d45fae801bd000d175c58148354cc',
                '--destination',
                'quay.io/wave:2f6d45fae801bd000d175c58148354cc',
                '--cache=true',
                '--cache-repo',
                'reg.io/wave/build/cache'
        ]
    }

}
