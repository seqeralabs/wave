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

package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class StringUtilsTest extends Specification {

    @Unroll
    def 'should strip secret' () {
        expect:
        StringUtils.redact(SECRET) == EXPECTED

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

    @Unroll
    def 'should get url protocol' () {
        expect:
        StringUtils.getUrlProtocol(STR)  == EXPECTED
        where:
        EXPECTED    | STR
        'ftp'       | 'ftp://abc.com'
        's3'        | 's3://bucket/abc'
        null        | 'gitzabc:xyz'
        null        | '/a/bc/'
    }

    @Unroll
    def 'should strip surreal prefix' () {
        expect:
        StringUtils.surrealId(ID) == EXPECTED

        where:
        ID                  | EXPECTED
        null                | null
        'foo'               | 'foo'
        and:
        'foo:100'           | '100'
        'foo-bar:1-2-3'     | '1-2-3'
        and:
        'foo:⟨100⟩'         | '100'
        'foo-bar:⟨1-2-3⟩'   | '1-2-3'
    }
}
