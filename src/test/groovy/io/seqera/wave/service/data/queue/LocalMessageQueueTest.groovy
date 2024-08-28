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

package io.seqera.wave.service.data.queue

import spock.lang.Specification

import java.time.Duration

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.data.queue.impl.LocalMessageQueue
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['test'])
class LocalMessageQueueTest extends Specification {

    @Inject
    private LocalMessageQueue broker

    def 'should return null if empty' () {
        expect:
        broker.poll('foo') == null

        when:
        def start = System.currentTimeMillis()
        and:
        broker.poll('foo', Duration.ofMillis(500)) == null
        and:
        def delta = System.currentTimeMillis()-start
        then:
        assert delta>=500
        assert delta<1000
    }

    def 'should offer and poll a value' () {
        given:
        broker.offer('bar', 'alpha')
        broker.offer('bar', 'beta')

        expect:
        broker.poll('foo') == null
        broker.poll('bar') == 'alpha'
        broker.poll('bar') == 'beta'
    }

}
