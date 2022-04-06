package io.seqera.storage

import io.seqera.storage.util.ZippedDigestStore
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ZippedDigestStoreTest extends Specification {

    def 'should load a lazy digest' () {
        given:
        def CONTENT = 'Hello world!'

        when:
        def digest = new ZippedDigestStore(CONTENT.bytes, 'text', 'sha256:122345567890')
        then:
        digest.inputStream.readAllBytes() == CONTENT.bytes
        digest.digest == 'sha256:122345567890'
        digest.mediaType == 'text'

    }

}
