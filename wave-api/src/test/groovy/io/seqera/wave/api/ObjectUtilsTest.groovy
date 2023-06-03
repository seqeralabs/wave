package io.seqera.wave.api

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ObjectUtilsTest extends Specification {

    @Unroll
    def 'should validate isEmpty string' () {
        expect:
        ObjectUtils.isEmpty((String)VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED 
        null        | true
        ''          | true
        'foo'       | false
    }

    @Unroll
    def 'should validate isEmpty Integer' () {
        expect:
        ObjectUtils.isEmpty((Integer)VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED
        null        | true
        0i          | true
        1i          | false
    }

    @Unroll
    def 'should validate isEmpty Long' () {
        expect:
        ObjectUtils.isEmpty((Long)VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED
        null        | true
        0l          | true
        1l          | false
    }

    @Unroll
    def 'should validate isEmpty List' () {
        expect:
        ObjectUtils.isEmpty((List)VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED
        null        | true
        []          | true
        [1]         | false
    }

    @Unroll
    def 'should validate isEmpty Map' () {
        expect:
        ObjectUtils.isEmpty((Map)VALUE) == EXPECTED
        where:
        VALUE       | EXPECTED
        null        | true
        [:]         | true
        [foo:1]     | false
    }
}
