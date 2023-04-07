package io.seqera.wave.service.data.future.impl

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalFutureQueueTest extends Specification {

    def 'should set and get a value' () {
        given:
        def queue = new LocalFutureQueue()

        expect:
        queue.poll('xyz') == null

        when:
        queue.offer('xyz', 'hello', null)
        then:
        queue.poll('xyz') == 'hello'
        and:
        queue.poll('xyz') == null
    }

}
