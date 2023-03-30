package io.seqera.wave.service.data.queue

import spock.lang.Specification

import groovy.transform.Canonical
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.data.queue.impl.LocalMessageBroker
import jakarta.inject.Inject

/**
 * Test class {@link AbstractMessageQueue} using a {@link LocalMessageBroker}
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@MicronautTest(environments = ['test'])
class AbstractMessageQueueLocalTest extends Specification {

    @Canonical
    static class Simple {
        String value
    }

    static class SimpleDataStream extends AbstractMessageQueue<Simple> {

        SimpleDataStream(MessageBroker<String> broker) {
            super(broker)
        }

        @Override
        String getPrefix() {
            return "local-test"
        }
    }

    @Inject
    private MessageBroker<String> broker


    def 'should register and unregister consumers'() {
        given:
        def stream = new SimpleDataStream(broker)

        when:
        stream.registerConsumer('service-key', 'consumer-id', {})
        then:
        stream.hasConsumer('service-key')

        when:
        stream.unregisterConsumer('service-key', 'consumer-id')
        then:
        !stream.hasConsumer('service-key')

    }

    def 'should send and consume a request'() {
        given:
        def stream = new SimpleDataStream(broker)
        def result = null

        when:
        stream.registerConsumer('service-key', 'consumer-id', { result = it })
        and:
        stream.sendMessage('service-key', new Simple('hello'))
        then:
        result.value == 'hello'

    }


}
