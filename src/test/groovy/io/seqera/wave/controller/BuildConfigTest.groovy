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

package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.ContainerPlatform

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildConfigTest extends Specification {

    def 'should return singularity image' () {
        given:
        BuildConfig config

        when:
        config = new BuildConfig(singularityImage: 'foo')
        then:
        config.singularityImage == 'foo'
        config.singularityImageArm64 == 'foo-arm64'
        and:
        config.singularityImage( ContainerPlatform.of('amd64') ) == 'foo'
        config.singularityImage( ContainerPlatform.of('arm64') ) == 'foo-arm64'

        when:
        config = new BuildConfig(singularityImage: 'foo', singularityImageArm64: 'bar')
        then:
        config.singularityImage == 'foo'
        config.singularityImageArm64 == 'bar'
        and:
        config.singularityImage( ContainerPlatform.of('amd64') ) == 'foo'
        config.singularityImage( ContainerPlatform.of('arm64') ) == 'bar'
    }

    @Unroll
    def 'should validate build max duration' () {
        given:
        def config = new BuildConfig(defaultTimeout: Duration.ofMinutes(DEFAULT), trustedTimeout: Duration.ofMinutes(TRUSTED))
        expect:
        config.buildMaxDuration(new SubmitContainerTokenRequest(towerAccessToken: TOKEN, freeze: FREEZE)) == Duration.ofMinutes(EXPECTED)

        where:
        TOKEN       | FREEZE        | DEFAULT       | TRUSTED       | EXPECTED
        null        | false         | 5             | 10            | 5
        null        | true          | 5             | 10            | 5
        null        | true          | 5             | 1             | 5
        'xyz'       | false         | 5             | 10            | 5
        'xtz'       | true          | 5             | 10            | 10    // <-- pick "trusted" because both "freeze" and "token" are provided
        'xtz'       | true          | 20            | 10            | 20    // <-- pick "default" when it's greater than "trusted"
    }
}
