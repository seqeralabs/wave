package io.seqera.wave.core

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerTest extends Specification {

    def 'should validate platform' () {
        expect:
        Container.platform(PLATFORM)  == EXPECTED

        where:
        PLATFORM        | EXPECTED
        null            | 'amd64'
        'x86_64'        | 'amd64'
        'amd64'         | 'amd64'
        'arm64'         | 'arm64'

    }
}
