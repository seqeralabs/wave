package io.seqera.util

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


}
