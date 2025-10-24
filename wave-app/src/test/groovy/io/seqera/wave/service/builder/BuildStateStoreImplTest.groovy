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

package io.seqera.wave.service.builder

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.builder.impl.BuildStateStoreImpl
import io.seqera.wave.store.state.CountParams
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class BuildStateStoreImplTest extends Specification {

    @Inject
    BuildStateStoreImpl store

    def 'should return entry key' () {
        expect:
        store.key0('foo') == 'wave-build/v2:foo'
    }

    def 'should return record id' () {
        expect:
        store.requestId0('foo') == 'wave-build/v2/request-id:foo'
    }

    def 'should return counter key' () {
        given:
        def build = Mock(BuildEntry) {
            getRequest()>>Mock(BuildRequest) { getContainerId()>>'12345' }
        }
        expect:
        store.counterKey('foo', build) == new CountParams('build-counters/v1','12345')
    }
}
