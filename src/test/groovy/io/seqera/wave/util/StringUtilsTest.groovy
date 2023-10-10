/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
