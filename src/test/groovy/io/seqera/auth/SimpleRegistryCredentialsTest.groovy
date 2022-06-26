package io.seqera.auth

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SimpleRegistryCredentialsTest extends Specification{

    def 'should get username and password' () {
        when:
        def creds = new SimpleRegistryCredentials('foo', 'bar')
        then:
        creds.username == 'foo'
        creds.password == 'bar'
    }

    def 'should implement hascode and equals' () {
        given:
        def creds1 = new SimpleRegistryCredentials('foo', 'bar')
        def creds2 = new SimpleRegistryCredentials('foo', 'bar')
        def creds3 = new SimpleRegistryCredentials('xxx', 'yyy')

        expect:
        creds1 == creds2
        creds1 != creds3
        and:
        creds1.hashCode() == creds2.hashCode()
        creds1.hashCode() != creds3.hashCode()

    }
}
