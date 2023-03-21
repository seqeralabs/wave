package io.seqera.wave.util

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ZipUtilsTest extends Specification {

    def 'should compress/decompress a text'() {
        given:
        def TEXT = 'Hello world\n' * 10

        when:
        def buffer = ZipUtils.compress(TEXT)
        then:
        buffer.size() > 0 && buffer.size() < TEXT.bytes.size()

        when:
        def copy = ZipUtils.decompressAsString(buffer)
        then:
        copy == TEXT

        when:
        def bytes = ZipUtils.decompressAsBytes(buffer)
        then:
        bytes == TEXT.bytes
    }

}
