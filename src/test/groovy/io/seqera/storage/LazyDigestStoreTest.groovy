package io.seqera.storage

import spock.lang.Specification

import java.nio.file.Files

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LazyDigestStoreTest extends Specification {

    def 'should load a lazy digest' () {
        given:
        def CONTENT = 'Hello world!'
        def file = Files.createTempFile('test', null)
        Files.write(file, CONTENT.bytes)

        when:
        def digest = new LazyDigestStore(file, 'text', 'sha256:122345567890')
        then:
        digest.bytes == CONTENT.bytes
        digest.digest == 'sha256:122345567890'
        digest.mediaType == 'text'

        cleanup:
        Files.deleteIfExists(file)
    }

}
