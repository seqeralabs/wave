package io.seqera.wave.util

import spock.lang.Specification

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class K8sHelperTest extends Specification {

    def 'should get platform selector' () {
        expect:
        K8sHelper.getSelectorLabel(ContainerPlatform.of(PLATFORM), SELECTORS) == EXPECTED

        where:
        PLATFORM        | SELECTORS                                             | EXPECTED
        'amd64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['foo': '1']
        'arm64'         | ['amd64': 'foo=1', 'arm64': 'bar=2']                  | ['bar': '2']
        and:
        'amd64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'x86_64'        | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['foo': '1']
        'arm64'         | ['linux/amd64': 'foo=1', 'linux/arm64': 'bar=2']      | ['bar': '2']

    }

    def 'should check unmatched platform' () {
        expect:
        K8sHelper.getSelectorLabel(ContainerPlatform.of('amd64'), [:]) == [:]

        when:
        K8sHelper.getSelectorLabel(ContainerPlatform.of('amd64'), [arm64:'x=1'])
        then:
        def err = thrown(BadRequestException)
        err.message == "Unsupported container platform 'linux/amd64'"
    }


}
