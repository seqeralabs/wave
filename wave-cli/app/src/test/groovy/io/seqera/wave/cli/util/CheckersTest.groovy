/*
 * Copyright 2023-2025, Seqera Labs
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

package io.seqera.wave.cli.util


import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CheckersTest extends Specification {

    @Unroll
    def 'should check if the string is empty' () {
        expect:
        Checkers.isEmpty(STR) == EXPECTED
        where:
        STR         | EXPECTED
        null        | true
        ''          | true
        '  '        | true
        'foo'       | false
    }

    @Unroll
    def 'should check if the list is empty' () {
        expect:
        Checkers.isEmpty(STR) == EXPECTED
        where:
        STR         | EXPECTED
        null        | true
        []          | true
        ['foo']     | false
    }

    @Unroll
    def 'should check env variable' () {
        expect:
        Checkers.isEnvVar(STR) == EXPECTED
        where:
        STR         | EXPECTED
        null        | false
        ''          | false
        'foo'       | false
        '='         | false
        '100=1'     | false
        and:
        'a=b'       | true
        'FOO=1'     | true
        'FOO='      | true

    }
}
