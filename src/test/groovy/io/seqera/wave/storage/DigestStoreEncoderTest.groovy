package io.seqera.wave.storage

import spock.lang.Specification

import io.seqera.wave.storage.reader.DataContentReader
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Deprecated
class DigestStoreEncoderTest extends Specification {

    def 'should encode and decode digest store classes' () {
        given:
        def CONTENT = 'Hello world!'
        and:
        def store = new ZippedDigestStore(CONTENT.bytes, 'text', 'sha256:122345567890')

        when:
        def encoded = DigestStoreEncoder.encode(store)
        and:
        def decoded = DigestStoreEncoder.decode(encoded)

        then:
        decoded.bytes == CONTENT.bytes
        decoded.digest == 'sha256:122345567890'
        decoded.mediaType == 'text'

    }

    def 'should encode and decode lazy digest store' () {
        given:
        def CONTENT = 'Hello world!'
        def data = new DataContentReader(CONTENT.bytes.encodeBase64().toString())

        and:
        def store = new LazyDigestStore(data, 'text', 'sha256:122345567890')

        when:
        def encoded = DigestStoreEncoder.encode(store)
        and:
        def decode = DigestStoreEncoder.decode(encoded)

        then:
        decode.bytes == CONTENT.bytes
        decode.digest == 'sha256:122345567890'
        decode.mediaType == 'text'

    }
}
