package io.seqera.wave.service.builder

import spock.lang.Specification

import io.seqera.wave.core.ContainerPlatform

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class KubeBuildStrategyTest extends Specification {

    def 'should get platform selector' () {
        given:
        def strategy = new KubeBuildStrategy()

        expect:
        strategy.getPlatformSelector(ContainerPlatform.of(PLATFORM), SELECTORS) == EXPECTED

        where:
        PLATFORM        | SELECTORS                                             | EXPECTED
        'amd64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['foo': '1']
        'arm64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['bar': '2']
        and:
        'amd64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'x86_64'        | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'arm64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['bar': '2']
        
    }

}
