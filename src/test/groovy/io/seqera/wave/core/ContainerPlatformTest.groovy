/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
