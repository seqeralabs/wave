/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
