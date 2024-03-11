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
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

import io.seqera.wave.util.JacksonHelper

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildResultTest extends Specification {

    def 'should check equals and hashcode' () {
        given:
        def now = Instant.now()
        def r1 = new BuildResult('100', 1, 'logs', now, Duration.ofMinutes(1))
        def r2 = new BuildResult('100', 1, 'logs', now, Duration.ofMinutes(1))
        def r3 = new BuildResult('101', 1, 'logs', now, Duration.ofMinutes(1))

        expect:
        r1 == r2
        r1 != r3
        and:
        r1.hashCode() == r2.hashCode()
        r1.hashCode() != r3.hashCode()
    }

    @Unroll
    def 'should validate status methods' () {
        when:
        def result = new BuildResult('100', EXIT, 'blah', Instant.now(), DURATION)
        then:
        result.done() == DONE
        result.succeeded() == SUCCEEDED
        result.failed() == FAILED

        where:
        EXIT    | DURATION              | DONE  | SUCCEEDED | FAILED
        0       | null                  | false | false     | false
        1       | null                  | false | false     | false
        and:
        0       | Duration.ofSeconds(1) | true  | true      | false
        1       | Duration.ofSeconds(1) | true  | false     | true
        
    }

    def 'should ser-deser build result' () {
        given:
        def result = new BuildResult('100', 1, 'logs', Instant.now(), Duration.ofMinutes(1))

        when:
        def json = JacksonHelper.toJson(result)
        then:
        noExceptionThrown()

        when:
        def copy = JacksonHelper.fromJson(json, BuildResult)
        then:
        copy == result
    }
}
