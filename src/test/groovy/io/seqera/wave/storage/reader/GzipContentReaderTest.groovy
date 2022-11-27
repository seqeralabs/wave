package io.seqera.wave.storage.reader

import spock.lang.Specification

import io.seqera.wave.util.ZipUtils

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class GzipContentReaderTest extends Specification {

    def 'should decode data' () {
        given:
        def DATA = 'Hola mundo!'

        when:
        def reader1 = GzipContentReader.fromPlainString(DATA)
        then:
        new String(reader1.readAllBytes()) == DATA

        when:
        final compressed = ZipUtils.compress(DATA);
        and:
        def reader2 = GzipContentReader.fromBase64EncodedString(compressed.encodeBase64().toString())
        then:
        new String(reader2.readAllBytes()) == DATA
    }
}
