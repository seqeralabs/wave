package io.seqera.wave.service.data.queue

import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.data.future.AbstractFutureStore
import io.seqera.wave.service.data.future.FuturePublisher
import io.seqera.wave.service.data.future.impl.RedisFuturePublisher
import io.seqera.wave.service.data.queue.impl.RedisQueueBroker
import io.seqera.wave.test.RedisTestContainer

/**
 *
 * @author Jordi Deu-Pons <jordi@jordeu.net>
 */
class AbstractConsumerQueueRedisTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST : redisHostName,
                REDIS_PORT : redisPort
        ], 'test', 'redis')

    }

    @Canonical
    static class Simple {
        final String value
    }

    static class SimpleConsumerQueue extends AbstractConsumerQueue<Simple> {

        SimpleConsumerQueue(QueueBroker<String> broker) {
            super(broker)
        }

        @Override
        String topic() {
            return 'redis-topic-x'
        }
    }


    def 'should consume values'  () {
        given:
        def broker = applicationContext.getBean(RedisQueueBroker)
        def queue = new SimpleConsumerQueue(broker)
        sleep(500) // Workaround to wait for Redis subscribers to be ready

        and: 'a consumer that adds foo values to foo_results'
        def foo_results = []
        queue.addConsumer('foo_one', { foo_results.add(it.value)})
        queue.addConsumer('foo_one', { foo_results.add(it.value)})
        and: 'a consumer that adds bar values to bar_results'
        def bar_results = []
        queue.addConsumer('bar_one', { bar_results.add(it.value)})
        queue.addConsumer('bar_one', { bar_results.add(it.value)})

        when:
        queue.send('foo_one', new Simple('foo01'))
        queue.send('foo_one', new Simple('foo02'))
        and:
        queue.send('none_one', new Simple('none01'))
        and:
        queue.send('bar_one', new Simple('bar01'))
        queue.send('bar_one', new Simple('bar02'))
        and:
        sleep(500)

        then:
        foo_results == ['foo01', 'foo02']
        and:
        bar_results == ['bar01', 'bar02']

        cleanup:
        queue.close()
    }

    def 'should consume values across instances' () {
        given:
        def broker1 = applicationContext.getBean(RedisQueueBroker)
        def queue1 = new SimpleConsumerQueue(broker1)
        and:
        def broker2 = applicationContext.getBean(RedisQueueBroker)
        def queue2 = new SimpleConsumerQueue(broker2)
        and:
        sleep(500) // Workaround to wait for Redis subscribers to be ready

        and: 'a consumer that adds foo values to foo_results'
        def foo_results = []
        queue1.addConsumer('foo_two', { foo_results.add(it.value)})
        queue1.addConsumer('foo_two', { foo_results.add(it.value)})
        and: 'a consumer that adds bar values to bar_results'
        def bar_results = []
        queue2.addConsumer('bar_two', { bar_results.add(it.value)})
        queue2.addConsumer('bar_two', { bar_results.add(it.value)})
        and:
        def onboth_results = []
        queue1.addConsumer('onboth', { onboth_results.add(it.value)})
        queue2.addConsumer('onboth', { onboth_results.add(it.value)})

        when:
        queue2.send('foo_two', new Simple('foo01'))
        queue2.send('foo_two', new Simple('foo02'))
        and:
        queue1.send('none_two', new Simple('none01'))
        queue2.send('none_two', new Simple('none02'))
        and:
        queue1.send('bar_two', new Simple('bar01'))
        queue1.send('bar_two', new Simple('bar02'))
        and:
        queue1.send('onboth', new Simple('onboth01'))
        and:
        sleep(500)

        then:
        foo_results == ['foo01', 'foo02']
        and:
        bar_results == ['bar01', 'bar02']
        and:
        onboth_results == ['onboth01']

        cleanup:
        queue1.close()
        queue2.close()
    }

}
