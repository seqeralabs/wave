package io.seqera.wave.storage

import spock.lang.Specification

import io.seqera.wave.storage.LazyDigestStore
import io.seqera.wave.storage.reader.DataContentReader
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LazyDigestStoreTest extends Specification {

    def 'should load a lazy digest' () {
        given:
        def CONTENT = 'Hello world!'
        and:
        def data = new DataContentReader(CONTENT.bytes.encodeBase64().toString())

        when:
        def digest = new LazyDigestStore(data, 'text', 'sha256:122345567890')
        then:
        digest.bytes == CONTENT.bytes
        digest.digest == 'sha256:122345567890'
        digest.mediaType == 'text'

    }

}
