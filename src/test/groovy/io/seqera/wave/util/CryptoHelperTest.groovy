package io.seqera.wave.util

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CryptoHelperTest extends Specification {

    def 'should validate hmac token' () {
        given:
        def message = 'Hello world'
        def secret = 'blah blah'

        expect:
        CryptoHelper.computeHmacSha256(message, secret) == CryptoHelper.computeHmacSha256(message, secret)
    }

}
