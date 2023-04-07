package io.seqera.wave.service.data.future.impl

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalFutureHashTest extends Specification {

    def 'should set and get a value' () {
        given:
        def queue = new LocalFutureHash()

        expect:
        queue.take('xyz') == null

        when:
        queue.put('xyz', 'hello', null)
        then:
        queue.take('xyz') == 'hello'
        and:
        queue.take('xyz') == null
    }

}
