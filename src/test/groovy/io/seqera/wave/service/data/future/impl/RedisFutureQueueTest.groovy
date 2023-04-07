package io.seqera.wave.service.data.future.impl

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RedisFutureQueueTest extends Specification implements RedisTestContainer  {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')

    }

    def 'should set and get a value' () {
        given:
        def queue = applicationContext.getBean(RedisFutureQueue)

        expect:
        queue.poll('xyz') == null

        when:
        queue.offer('xyz', 'hello', Duration.ofSeconds(5))
        then:
        queue.poll('xyz') == 'hello'
        and:
        queue.poll('xyz') == null
    }
}
