package io.seqera.wave.service.data.future

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.seqera.wave.service.data.future.impl.RedisFuturePublisher
import io.seqera.wave.test.RedisTestContainer
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AbstractFutureStoreRedisTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST : redisHostName,
                REDIS_PORT : redisPort
        ], 'test', 'redis')

    }

    /*
     * A simple value class to be stored
     */
    @Canonical
    static class Simple {
        final String value
    }

    /*
     * simple redis data store
     */
    static class SimpleRedisDataStore extends AbstractFutureStore<Simple> {

        SimpleRedisDataStore(FuturePublisher<String> publisher) {
            super(publisher, Duration.ofSeconds(5))
        }

        @Override
        String group() {
            return 'redis-topic-x'
        }
    }


    def 'should store future values' () {
        given:
        def pub = applicationContext.getBean(RedisFuturePublisher)
        def store = new SimpleRedisDataStore(pub)
        and:
        sleep(500) // Workaround to wait for Redis subscribers to be ready

        when:
        def future = store.create('foo_one')
        then:
        !future.isDone()

        when:
        store.complete('bar_one', new Simple('Hola'))
        then:
        !future.isDone()

        when:
        store.complete('foo_one', new Simple('Hello'))
        then:
        future.get(5, TimeUnit.SECONDS).value == "Hello"

        cleanup:
        store.close()
    }

    def 'should store future values across instances' () {
        when:
        def pub1 = applicationContext.getBean(RedisFuturePublisher)
        def store1 = new SimpleRedisDataStore(pub1)
        and:
        def pub2 = applicationContext.getBean(RedisFuturePublisher)
        def store2 = new SimpleRedisDataStore(pub2)
        and:
        sleep(500) // Workaround to wait for Redis subscribers to be ready

        // create a future on store1
        and:
        def future = store1.create('foo_two')
        then:
        !future.isDone()

        // complete it on store2
        when:
        store2.complete('foo_two', new Simple('Hola'))
        then:
        // future get completed
        future.get(5, TimeUnit.SECONDS).value == "Hola"

        cleanup:
        store1.close()
        store2.close()
    }
}
