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
        stream.registerConsumer('service-key', {})
        then:
        stream.hasConsumer('service-key')

        when:
        stream.unregisterConsumer('service-key')
        then:
        !stream.hasConsumer('service-key')

    }

    def 'should send and consume a request'() {
        given:
        def stream = new SimpleDataStream(broker)

        when:
        def result = new CompletableFuture()
        stream.registerConsumer('service-key',  { result.complete(it) })
        and:
        stream.sendMessage('service-key', new Simple('hello'))
        then:
        result.get().value == 'hello'

    }


}
