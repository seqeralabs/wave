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

package io.seqera.wave.service.mirror

import spock.lang.Specification

import java.time.Duration

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.MirrorConfig
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class MirrorStateStoreTest extends Specification {

    @Inject
    MirrorStateStore store

    @MockBean(MirrorConfig)
    MirrorConfig mockConfig() {
        Mock(MirrorConfig)
    }

    @Inject
    MirrorConfig config

    def 'should return entry key' () {
        expect:
        store.key0('foo') == 'wave-mirror/v1:foo'
    }

    def 'should return record id' () {
        expect:
        store.requestId0('foo') == 'wave-mirror/v1/request-id:foo'
    }

    def 'should put entry' () {
        given:
        def entry = Mock(MirrorEntry)

        when:
        store.putEntry(entry)
        then:
        entry.key >> 'one'
        entry.requestId >> '123'
        entry.result >> Mock(MirrorResult) {  succeeded()>>true }
        and:
        1 * config.statusDuration >> Duration.ofSeconds(1)
        0 * config.failureDuration >> null
        and:
        store.get('one')

        when:
        store.putEntry(entry)
        then:
        entry.key >> 'two'
        entry.requestId >> '123'
        entry.result >> Mock(MirrorResult) {  succeeded()>>false }
        and:
        0 * config.statusDuration >> null
        1 * config.failureDuration >>  Duration.ofSeconds(1)
        and:
        store.get('two')
    }
}
