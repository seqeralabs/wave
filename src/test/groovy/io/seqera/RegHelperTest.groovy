package io.seqera

import io.seqera.controller.RegHelper
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RegHelperTest extends Specification {

    def 'should compute digest' () {
        expect:
        RegHelper.digest(Mock.MANIFEST_LIST_CONTENT) == Mock.MANIFEST_DIGEST
    }

    def 'should encode base32' () {
        expect:
        RegHelper.encodeBase32('Simple Solution') == 'knuw24dmmuqfg33mov2gs33o'
        RegHelper.encodeBase32('Hello world') == 'jbswy3dpeb3w64tmmq______'
        RegHelper.encodeBase32('Hello world', false) == 'jbswy3dpeb3w64tmmq'
    }

    def 'should decode base32' () {
        expect:
        RegHelper.decodeBase32('knuw24dmmuqfg33mov2gs33o') == 'Simple Solution'
        RegHelper.decodeBase32('jbswy3dpeb3w64tmmq______') == 'Hello world'
        RegHelper.decodeBase32('jbswy3dpeb3w64tmmq') == 'Hello world'
    }

    def 'should validate encode no padding' () {
        given:
        List<String> words = []
        List<String> encoded = []
        and:
        for( int x : 0..100 ) {
            def w = RegHelper.randomString(5,20)
            words.add(w)
            encoded.add( RegHelper.encodeBase32(w, false) )
        }

        when:
        def decoded = []
        for( int x : 0..100 ) {
            decoded.add( RegHelper.decodeBase32(encoded[x]) )
        }
        then:
        words == decoded
    }


    def 'should generate random hex' () {
        expect:
        RegHelper.random256Hex().length() == 64
    }
}
