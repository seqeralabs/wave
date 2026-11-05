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

package io.seqera.wave.tower.auth

import spock.lang.Specification

import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class JwtTimeLocalTest extends Specification {

    @Inject
    JwtTimeStore timer

    def 'should add and get token timers' () {
        given:
        def now = Instant.now().epochSecond

        expect:
        timer.getRange(0, now, 10) == []

        when:
        timer.add('foo', now-1)
        timer.add('bar', now-2)
        then:
        timer.getRange(0, now, 1) == ['bar']
        timer.getRange(0, now, 1) == ['foo']
        timer.getRange(0, now, 1) == []

        when:
        timer.add('foo', now+1)
        timer.add('bar', now+2)
        then:
        timer.getRange(0, now, 1) == []
        and:
        timer.getRange(0, now+5, 5) == ['foo','bar']
        and:
        timer.getRange(0, now+5, 5) == []
    }

    def 'should keep earlier score when using addIfLess' () {
        given:
        def now = Instant.now().epochSecond

        when:
        timer.addIfLess('foo', now+10)
        then: 'new member is added'
        timer.getRange(0, now+20, 5) == ['foo']

        when:
        timer.addIfLess('foo', now+10)
        timer.addIfLess('foo', now+20)
        then: 'later score is ignored, earlier score wins'
        timer.getRange(0, now+15, 5) == ['foo']
        timer.getRange(0, now+15, 5) == []

        when:
        timer.addIfLess('foo', now+20)
        timer.addIfLess('foo', now+5)
        then: 'earlier score replaces the existing one'
        timer.getRange(0, now+10, 5) == ['foo']
        timer.getRange(0, now+10, 5) == []
    }

}
