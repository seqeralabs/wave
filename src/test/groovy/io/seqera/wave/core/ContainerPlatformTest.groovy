package io.seqera.wave.core

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerPlatformTest extends Specification {

    @Unroll
    def 'should validate platform' () {
        expect:
        ContainerPlatform.of(PLATFORM) == EXPECTED
        ContainerPlatform.of(PLATFORM).toString() == STRING

        where:
        PLATFORM        | EXPECTED                                      | STRING
        null            | new ContainerPlatform('linux','amd64')        | 'linux/amd64'
        'x86_64'        | new ContainerPlatform('linux','amd64')        | 'linux/amd64'
        'amd64'         | new ContainerPlatform('linux','amd64')        | 'linux/amd64'
        'linux/amd64'   | new ContainerPlatform('linux','amd64')        | 'linux/amd64'
        and:
        'arm64'         | new ContainerPlatform('linux','arm64','v8')   | 'linux/arm64/v8'
        'linux/arm64'   | new ContainerPlatform('linux','arm64','v8')   | 'linux/arm64/v8'
        and:
        'linux/arm/v7'  | new ContainerPlatform('linux','arm','v7')     | 'linux/arm/v7'

    }

}
