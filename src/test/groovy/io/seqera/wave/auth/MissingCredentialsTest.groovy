package io.seqera.wave.auth

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MissingCredentialsTest extends Specification {

    def 'should check equals and hash code' () {
        given:
        def c1 = new MissingCredentials('a')
        def c2 = new MissingCredentials('a')
        def c3 = new MissingCredentials('z')

        expect:
        c1 == c2
        c1 != c3
        and:
        c1.hashCode() == c2.hashCode()
        c1.hashCode() != c3.hashCode()
    }

    def 'should check groovy truth' () {
        expect:
        !new MissingCredentials('a')
    }
}
