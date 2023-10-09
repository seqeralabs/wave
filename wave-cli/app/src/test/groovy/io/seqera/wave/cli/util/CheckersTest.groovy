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
