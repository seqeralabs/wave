package io.seqera.wave.service.data.queue

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import io.micronaut.context.ApplicationContext
import io.seqera.wave.service.data.queue.impl.RedisQueueBroker
import io.seqera.wave.service.pairing.socket.PairingOutboundQueue
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.test.RedisTestContainer
/**
 * Test class {@link AbstractMessageQueue} using a {@link RedisQueueBroker}
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
class AbstractMessageQueueRedisTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')

    }


    def 'should register and unregister consumers'() {
        given:
        def broker = applicationContext.getBean(RedisQueueBroker)
        def queue = new PairingOutboundQueue(broker, Duration.ofMillis(100))

        when:
        queue.registerClient('service-key-one', '123',  {})
        then:
        queue.hasTarget('service-key-one')

        when:
        queue.unregisterClient('service-key-one', '123')
        then:
        !queue.hasTarget('service-key-one')

        cleanup:
        queue.close()
    }

    def 'should send and consume a request'() {
        given:
        def broker = applicationContext.getBean(RedisQueueBroker)
        def queue = new PairingOutboundQueue(broker, Duration.ofMillis(100))
        and:
        def result = new CompletableFuture<PairingMessage>()
        when:
        queue.registerClient('service-key-two', '123', { result.complete(it) })
        and:
        queue.queueMessage('service-key-two', new PairingHeartbeat('xyz'))
        then:
        result.get(1,TimeUnit.SECONDS).msgId == 'xyz'

        cleanup:
        queue.close()
    }


    def 'should send and consume a request across instances'() {
        given:
        def broker1 = applicationContext.getBean(RedisQueueBroker)
        def queue1 = new PairingOutboundQueue(broker1, Duration.ofMillis(100))
        and:
        def broker2 = applicationContext.getBean(RedisQueueBroker)
        def queue2 = new PairingOutboundQueue(broker2, Duration.ofMillis(100))
        and:
        def result = new CompletableFuture<PairingMessage>()

        when:
        queue2.registerClient('service-key-three', '123', { result.complete(it) })
        and:
        queue1.queueMessage('service-key-three', new PairingHeartbeat('123'))
        then:
        result.get(1,TimeUnit.SECONDS).msgId == '123'

        cleanup:
        queue1.close()
        queue2.close()
    }

    def 'should check register and unregister consumers across instances'() {
        given:
        def broker1 = applicationContext.getBean(RedisQueueBroker)
        def queue1 = new PairingOutboundQueue(broker1, Duration.ofMillis(100))
        and:
        def broker2 = applicationContext.getBean(RedisQueueBroker)
        def queue2 = new PairingOutboundQueue(broker2, Duration.ofMillis(100))

        when:
        queue1.registerClient('service-key-four', '123', {})
        and:
        sleep(100)
        then:
        queue2.hasTarget('service-key-four')

        when:
        queue1.unregisterClient('service-key-four', '123')
        and:
        sleep(100)
        then:
        !queue1.hasTarget('service-key-four')

        cleanup:
        queue1.close()
        queue2.close()
    }


}
