package io.seqera.util

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TapInputStreamFilterTest extends Specification {

    def 'should copy the source stream to another stream' () {

        given:
        def data = ('Hello world' * 100).bytes
        def source = new ByteArrayInputStream(data)
        def target = new ByteArrayOutputStream()
        def download = new ByteArrayOutputStream()

        and:
        def tap = new TapInputStreamFilter(source, target)

        when:
        tap.transferTo(download)
        and:
        tap.close()
        download.close()

        then:
        new String(download.toByteArray()) == new String(data)
        and:
        new String(target.toByteArray()) == new String(data)
    }
}
