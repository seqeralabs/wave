package io.seqera.wave.service.data.queue

import spock.lang.Specification

import groovy.transform.Canonical
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.data.future.AbstractFutureStore
import io.seqera.wave.service.data.future.FuturePublisher
import jakarta.inject.Inject

/**
 *
 * @author Jordi Deu-Pons <jordi@jordeu.net>
 */
@MicronautTest(environments = ['test'])
class AbstractConsumerQueueLocalTest extends Specification {

    @Canonical
    static class Simple {
        String value
    }

    static class SimpleConsumerQueue extends AbstractConsumerQueue<Simple> {

        SimpleConsumerQueue(QueueBroker<String> broker) {
            super(broker)
        }

        @Override
        String group() {
            // not needed for local implementation
            return null
        }
    }

    @Inject
    private QueueBroker<String> broker


    def 'should consume values' () {
        given: 'a consumer queue'
        def queue = new SimpleConsumerQueue(broker)
        and: 'a consumer that adds foo values to foo_results'
        def foo_results = []
        queue.addConsumer('foo', { foo_results.add(it.value)})
        queue.addConsumer('foo', { foo_results.add(it.value)})
        and: 'a consumer that adds bar values to bar_results'
        def bar_results = []
        queue.addConsumer('bar', { bar_results.add(it.value)})
        queue.addConsumer('bar', { bar_results.add(it.value)})

        when:
        queue.send('foo', new Simple('foo01'))
        queue.send('foo', new Simple('foo02'))
        and:
        queue.send('none', new Simple('none01'))
        queue.send('none', new Simple('none02'))
        and:
        queue.send('bar', new Simple('bar01'))
        queue.send('bar', new Simple('bar02'))

        then:
        foo_results == ['foo01', 'foo02']
        and:
        bar_results == ['bar01', 'bar02']

    }

}
