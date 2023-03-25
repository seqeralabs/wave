package io.seqera.wave.service.data.future

import spock.lang.Specification

import java.time.Duration

import groovy.transform.Canonical
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['test'])
class AbstractFutureStoreLocalTest extends Specification {

    @Canonical
    static class Simple {
        String value
    }

    static class SimpleDataStore extends AbstractFutureStore<Simple> {

        SimpleDataStore(FuturePublisher<String> publisher) {
            super(publisher, Duration.ofSeconds(5))
        }

        @Override
        String topic() {
            // not needed for local implementation
            return null
        }
    }

    @Inject
    private FuturePublisher<String> publisher


    def 'should store future values' () {
        given:
        def store = new SimpleDataStore(publisher)

        when:
        def future = store.create('foo')
        then:
        !future.isDone()

        when:
        store.complete('bar', new Simple('Hola'))
        then:
        !future.isDone()

        when:
        store.complete('foo', new Simple('Hello'))
        then:
        future.isDone()
        future.get().value == "Hello"
    }

}
