package io.seqera.wave.service.cache.impl

import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer

class RedisCacheProviderTest extends Specification implements RedisTestContainer {

    ApplicationContext applicationContext

    RedisCacheProvider redisCacheProvider

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST : redisHostName,
                REDIS_PORT : redisPort
        ], 'test', 'redis')
        redisCacheProvider = applicationContext.getBean(RedisCacheProvider)
    }

    def 'conditional put with current value when ke is not set'() {
        when: 'conditionally set a key that has no current value'
        def current = redisCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofSeconds(100))

        then: 'the provided value is returned'
        current == 'new-value'

        and: 'the value is set in the store'
        redisCacheProvider.get('key') == 'new-value'
    }

    def 'conditional put with current value when key is already set'() {
        given: 'a store containing a mapping for key that is not expired'
        redisCacheProvider.put('key', 'existing', Duration.ofSeconds(100))

        when: 'try to conditionally set the key to a new value'
        def current = redisCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofSeconds(100))

        then: 'the existing value is returned'
        current == 'existing'

        and: 'the value is not updated in the store'
        redisCacheProvider.get('key') == 'existing'

    }

    def 'conditional put with current value when key is set and has expired'() {
        given: 'a store containing a mapping for key that will expire'
        redisCacheProvider.put('key', 'existing', Duration.ofSeconds(1))
        // give time for redis to expire the key
        sleep(Duration.ofSeconds(2).toMillis())

        when: 'try to conditionally set the key to a new value'
        def current = redisCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofSeconds(100))

        then: 'the provided value is returned'
        current == 'new-value'

        and: 'the value is updated is set in the store'
        redisCacheProvider.get('key') == 'new-value'
    }

}
