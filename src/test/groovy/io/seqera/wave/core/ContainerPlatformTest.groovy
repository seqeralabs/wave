/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

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
        'arm'           | new ContainerPlatform('linux','arm','v7')     | 'linux/arm/v7'
        'linux/arm'     | new ContainerPlatform('linux','arm','v7')     | 'linux/arm/v7'
        'linux/arm/7'   | new ContainerPlatform('linux','arm','v7')     | 'linux/arm/v7'
        'linux/arm/v7'  | new ContainerPlatform('linux','arm','v7')     | 'linux/arm/v7'
        'linux/arm/5'   | new ContainerPlatform('linux','arm','v5')     | 'linux/arm/v5'
        'linux/arm/v5'  | new ContainerPlatform('linux','arm','v5')     | 'linux/arm/v5'
        and:
        'arm64'         | new ContainerPlatform('linux','arm64')        | 'linux/arm64'
        'linux/arm64'   | new ContainerPlatform('linux','arm64')        | 'linux/arm64'
        'linux/aarch64' | new ContainerPlatform('linux','arm64')        | 'linux/arm64'
        and:
        'linux/arm64/v8'| new ContainerPlatform('linux','arm64')        | 'linux/arm64'
        'linux/arm64/v7'| new ContainerPlatform('linux','arm64','v7')   | 'linux/arm64/v7'

    }

    @Unroll
    def 'should match' () {
        expect:
        ContainerPlatform.of(PLATFORM).matches(RECORD)
        where:
        PLATFORM        | RECORD
        'amd64'         | [os:'linux', architecture:'amd64']
        'amd64'         | [os:'linux', architecture:'x86_64']
        'linux/amd64'   | [os:'linux', architecture:'amd64']
        and:
        'arm64'         | [os:'linux', architecture:'arm64']
        'arm64'         | [os:'linux', architecture:'arm64', variant: 'v8']
        'linux/arm64'   | [os:'linux', architecture:'arm64', variant: 'v8']
        'linux/aarch64' | [os:'linux', architecture:'arm64', variant: 'v8']
        'linux/arm64'   | [os:'linux', architecture:'aarch64', variant: 'v8']
        'linux/arm64/8' | [os:'linux', architecture:'arm64', variant: 'v8']
        'linux/arm64/v8'| [os:'linux', architecture:'arm64', variant: 'v8']
        and:
        'arm'           | [os:'linux', architecture:'arm', variant: 'v7']
        'linux/arm/v7'  | [os:'linux', architecture:'arm', variant: 'v7']
        'linux/arm/v5'  | [os:'linux', architecture:'arm', variant: 'v5']
    }

    @Unroll
    def 'should not match' () {
        expect:
        !ContainerPlatform.of(PLATFORM).matches(RECORD)
        where:
        PLATFORM        | RECORD
        'amd64'         | [os:'linux', architecture:'arm64']
        'windows/amd64' | [os:'linux', architecture:'amd64']
        and:
        'linux/arm64/v7'| [os:'linux', architecture:'arm64']
        'linux/arm64/v7'| [os:'linux', architecture:'arm64', variant: 'v8']
        'linux/arm64/v8'| [os:'linux', architecture:'arm64', variant: 'v9']

    }
}
