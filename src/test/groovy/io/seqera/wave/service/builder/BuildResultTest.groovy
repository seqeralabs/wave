package io.seqera.wave.service.builder

import spock.lang.Specification

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
