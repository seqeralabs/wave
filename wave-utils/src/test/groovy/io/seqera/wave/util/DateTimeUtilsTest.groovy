package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DateTimeUtilsTest extends Specification {

    def 'should get timestamp zone id' () {
        given:
        def ts1 = '2022-10-20T17:00:00.00Z'
        def ts2 = '2022-10-20T17:00:00.00+02:00'

        expect:
        DataTimeUtils.offsetId(ts1) == 'Z'
        DataTimeUtils.offsetId(ts2) == '+02:00'
    }

    def 'should format ts' () {
        given:
        def ts1 = Instant.parse('2022-10-20T17:00:00.00Z')

        expect:
        DataTimeUtils.formatTimestamp(null,null) == null 
        DataTimeUtils.formatTimestamp(ts1, 'Z') == '2022-10-20 17:00 (GMT)'
        DataTimeUtils.formatTimestamp(ts1, '+02:00') == '2022-10-20 19:00 (GMT+2)'
    }

    def 'should format offsetdatetime' () {
        given:
        def ts1 = OffsetDateTime.parse('2022-10-20T17:00:00.00Z')

        expect:
        DataTimeUtils.formatTimestamp(null) == null
        DataTimeUtils.formatTimestamp(ts1) == '2022-10-20 17:00 (GMT)'
    }

    @Unroll
    def 'should format duration'  () {
        expect:
        DataTimeUtils.formatDuration(DURATION) == EXPECTED

        where:
        DURATION                   | EXPECTED
        null                       | null
        Duration.ofSeconds(10)     | '0:10'
        Duration.ofSeconds(60)     | '1:00'
        Duration.ofSeconds(90)     | '1:30'
        Duration.ofSeconds(130)    | '2:10'
        Duration.ofMinutes(60)     | '60:00'
    }
}
