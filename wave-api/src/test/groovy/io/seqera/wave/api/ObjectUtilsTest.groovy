/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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

    def 'should render a list as a string'  () {
        expect:
        ObjectUtils.toString(LIST) == EXPECTED

        where:
        LIST            | EXPECTED
        null            | null
        []              | '(empty)'
        ['1']           | '1'
        ['1','2','3']   | '1,2,3'
    }

    @Unroll
    def 'should strip secret' () {
        expect:
        ObjectUtils.redact(SECRET) == EXPECTED

        where:
        SECRET          | EXPECTED
        'hi'            | '****'
        'Hello'         | 'Hel****'
        'World'         | 'Wor****'
        '12345678'      | '123****'
        'hola'          | '****'
        null            | '(null)'
        ''              | '(empty)'
    }
}
