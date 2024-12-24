package io.seqera.wave.service.aws.cache

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
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

    def 'should cache user info response'() {
        given:
        def AWAIT = 150
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new AwsEcrCache(store, Duration.ofMillis(AWAIT), 100)
        def cache2 = new AwsEcrCache(store, Duration.ofMillis(AWAIT), 100)
        and:
        def k = UUID.randomUUID().toString()
        def resp = "aaaa"

        when:
        cache1.put(k, resp)
        then:
        cache2.get(k) == resp
    }
}
