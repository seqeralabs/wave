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
