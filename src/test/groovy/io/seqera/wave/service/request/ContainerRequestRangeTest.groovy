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

package io.seqera.wave.service.request

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerRequestRangeTest extends Specification {

    @Inject
    ContainerRequestRange range

    def 'should return an empty list when no entries are avail' () {
        expect:
        range.getEntriesUntil(Instant.now(), 10) == []
    }

    def 'should add and retrieve some values' () {
        given:
        def now = Instant.now()
        and:
        def e1 = new ContainerRequestRange.Entry('cr-1', 'wf-1',now)
        def e2 = new ContainerRequestRange.Entry('cr-2', 'wf-2',now)
        def e3 = new ContainerRequestRange.Entry('cr-3', 'wf-3',now)
        and:
        range.add(e1, now- Duration.ofSeconds(2))
        range.add(e2, now- Duration.ofSeconds(1))
        range.add(e3, now+ Duration.ofMillis(600))

        expect:
        range.getEntriesUntil(now, 10) == [e1, e2]
        and:
        range.getEntriesUntil(now.plusSeconds(1), 10) == [e3]
    }

}
