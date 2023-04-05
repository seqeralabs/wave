package io.seqera.wave.service.data.queue

import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import groovy.transform.Canonical
import io.micronaut.test.extensions.spock.annotation.MicronautTest
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
        stream.registerClient('service-key', '123', {})
        then:
        stream.hasClient('service-key')

        when:
        stream.unregisterClient('service-key', '123',)
        then:
        !stream.hasClient('service-key')

    }

    def 'should send and consume a request'() {
        given:
        def stream = new SimpleDataStream(broker)

        when:
        def result = new CompletableFuture()
        stream.registerClient('service-key', '123', { result.complete(it) })
        and:
        stream.sendMessage('service-key', new Simple('hello'))
        then:
        result.get().value == 'hello'

    }


}
