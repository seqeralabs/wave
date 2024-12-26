package io.seqera.wave.service.aws.cache

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.store.cache.RedisL2TieredCache
import io.seqera.wave.test.RedisTestContainer

class AwsEcrCacheTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
        sleep(500) // workaround to wait for Redis connection
    }

    def cleanup() {
        applicationContext.close()
    }

    def 'should cache ecr token response'() {
        given:
        def AWAIT = 150
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new AwsEcrCache(store)
        def cache2 = new AwsEcrCache(store)
        and:
        def k = UUID.randomUUID().toString()
        def token = new Token('token')

        when:
        cache1.put(k, token, Duration.ofSeconds(30))
        then:
        cache2.get(k) == token
    }
}
