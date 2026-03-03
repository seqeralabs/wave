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

import io.seqera.wave.exception.BadRequestException

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

    def 'should return an exception' () {
        when:
        ContainerPlatform.of(null)
        then:
        thrown(BadRequestException)

        when:
        ContainerPlatform.of('foo')
        then:
        thrown(BadRequestException)
    }

    def 'should parse the platform or return default' () {
        expect:
        ContainerPlatform.parseOrDefault(PLATFORM) == EXPECTED
        ContainerPlatform.parseOrDefault(PLATFORM).toString() == STRING

        where:
        PLATFORM        | EXPECTED                                      | STRING
        null            | new ContainerPlatform('linux','amd64')        | 'linux/amd64'
        'x86_64'        | new ContainerPlatform('linux','amd64')        | 'linux/amd64'
        'arm64'         | new ContainerPlatform('linux','arm64')        | 'linux/arm64'
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

    def 'should parse multi-arch platform' () {
        when:
        def platform = ContainerPlatform.of('linux/amd64,linux/arm64')
        then:
        platform.os == 'linux'
        platform.arch == 'amd64'
        platform.platforms == [new ContainerPlatform.Platform('linux','amd64'), new ContainerPlatform.Platform('linux','arm64')]
        platform.isMultiArch()
        platform.toString() == 'linux/amd64,linux/arm64'
    }

    def 'should detect single-arch platform' () {
        when:
        def platform = ContainerPlatform.of('linux/amd64')
        then:
        platform.os == 'linux'
        platform.arch == 'amd64'
        platform.platforms == [new ContainerPlatform.Platform('linux','amd64')]
        !platform.isMultiArch()
        platform.toString() == 'linux/amd64'
    }

    def 'should round-trip multi-arch through toString and of' () {
        when:
        def original = ContainerPlatform.of('linux/amd64,linux/arm64')
        def roundTripped = ContainerPlatform.of(original.toString())
        then:
        roundTripped == original
        roundTripped.isMultiArch()
        roundTripped.platforms == [new ContainerPlatform.Platform('linux','amd64'), new ContainerPlatform.Platform('linux','arm64')]
    }

    def 'should have MULTI_PLATFORM constant' () {
        expect:
        ContainerPlatform.MULTI_PLATFORM.isMultiArch()
        ContainerPlatform.MULTI_PLATFORM.os == 'linux'
        ContainerPlatform.MULTI_PLATFORM.arch == 'amd64'
        ContainerPlatform.MULTI_PLATFORM.platforms == [new ContainerPlatform.Platform('linux','amd64'), new ContainerPlatform.Platform('linux','arm64')]
        ContainerPlatform.MULTI_PLATFORM.toString() == 'linux/amd64,linux/arm64'
    }

    def 'should test equality for multi-arch' () {
        expect:
        ContainerPlatform.of('linux/amd64,linux/arm64') == ContainerPlatform.MULTI_PLATFORM
        ContainerPlatform.of('linux/amd64,linux/arm64') == ContainerPlatform.of('linux/amd64,linux/arm64')
        ContainerPlatform.of('linux/amd64') != ContainerPlatform.MULTI_PLATFORM
    }
}
