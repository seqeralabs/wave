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
class RedisFutureHashTest extends Specification implements RedisTestContainer  {

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
        def queue = applicationContext.getBean(RedisFutureHash)

        expect:
        queue.take('xyz') == null

        when:
        queue.put('xyz', 'hello', Duration.ofSeconds(5))
        then:
        queue.take('xyz') == 'hello'
        and:
        queue.take('xyz') == null
    }
}
