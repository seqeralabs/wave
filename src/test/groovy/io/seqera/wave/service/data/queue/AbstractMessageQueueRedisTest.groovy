package io.seqera.wave.service.data.queue

import spock.lang.Shared
import spock.lang.Specification

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.data.queue.impl.RedisQueueBroker
import io.seqera.wave.test.RedisTestContainer

/**
 * Test class {@link AbstractMessageQueue} using a {@link RedisMessageBroker}
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@MicronautTest(environments = ['test'])
class AbstractMessageQueueRedisTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')

    }

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

    def 'should register and unregister consumers'() {
        given:
        def broker = applicationContext.getBean(RedisQueueBroker)
        def stream = new SimpleDataStream(broker)

        when:
        stream.registerConsumer('service-key-one',  {})
        and:
        sleep(500)
        then:
        stream.hasConsumer('service-key-one')

        when:
        stream.unregisterConsumer('service-key-one')
        and:
        sleep(500)
        then:
        !stream.hasConsumer('service-key-one')

    }

    def 'should send and consume a request'() {
        given:
        def broker = applicationContext.getBean(RedisQueueBroker)
        def stream = new SimpleDataStream(broker)
        def result = null

        when:
        stream.registerConsumer('service-key-two', { result = it })
        and:
        stream.sendMessage('service-key-two', new Simple('hello'))
        and:
        sleep(500)
        then:
        result.value == 'hello'

    }

    def 'should send and consume a request across instances'() {
        given:
        def broker1 = applicationContext.getBean(RedisQueueBroker)
        def stream1 = new SimpleDataStream(broker1)
        and:
        def broker2 = applicationContext.getBean(RedisQueueBroker)
        def stream2 = new SimpleDataStream(broker2)
        and:
        def result = null

        when:
        stream2.registerConsumer('service-key-three',  { result = it })
        and:
        stream1.sendMessage('service-key-three', new Simple('hello'))
        and:
        sleep(500)
        then:
        result.value == 'hello'

    }


}
