package io.seqera.wave.util

import spock.lang.Specification

import java.time.Instant

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
        DataTimeUtils.toOffsetId(ts1) == 'Z'
        DataTimeUtils.toOffsetId(ts2) == '+02:00'
    }

    def 'should format ts' () {
        given:
        def ts1 = Instant.parse('2022-10-20T17:00:00.00Z')

        expect:
        DataTimeUtils.formatTimestamp(null,null) == null 
        DataTimeUtils.formatTimestamp(ts1, 'Z') == '2022-10-20 17:00'
        DataTimeUtils.formatTimestamp(ts1, '+02:00') == '2022-10-20 19:00'
    }
}
