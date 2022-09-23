package io.seqera.wave.service.builder.cache

import spock.lang.Specification
import spock.lang.Timeout

import java.time.Instant
import java.util.concurrent.ExecutionException

import io.micronaut.context.ApplicationContext
import io.seqera.wave.exception.BuildTimeoutException
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStore
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.JedisPool
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RedisBuildStoreTest extends Specification implements RedisTestContainer {

    ApplicationContext applicationContext

    JedisPool jedisPool

    def setup() {
        restartRedis()
        applicationContext = ApplicationContext.run([
                wave:[ build:[ timeout: '5s' ]],
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
        jedisPool = new JedisPool(redisHostName, redisPort as int)
    }

    def 'should get and put key values' () {
        given:
        def req1 = BuildResult.create('1')
        and:
        def cacheStore = applicationContext.getBean(BuildStore)
        
        expect:
        cacheStore.getBuild('foo') == null
        and:
        !cacheStore.hasBuild('foo')

        when:
        cacheStore.storeBuild('foo', req1)
        then:
        cacheStore.getBuild('foo') == req1
        and:
        jedisPool.resource.get("wave-build:foo").toString()
        and:
        cacheStore.hasBuild('foo')
    }

    @Timeout(value=10)
    def 'should await for a value' () {
        given:
        def req1 = BuildResult.create('1')
        and:
        def cacheStore = applicationContext.getBean(BuildStore) as BuildStore

        when:
        // insert a value
        cacheStore.storeBuild('foo', req1)

        // update a value in a separate thread
        Thread.start {
            req1 = BuildResult.completed('1', 0, '', Instant.now())
            cacheStore.storeBuild('foo',req1)
        }

        // wait the value is updated
        sleep 1500
        def result = cacheStore.awaitBuild('foo')

        then:
        result.get() == req1
    }

    @Timeout(value=30)
    def 'should abort an await if build never finish' () {
        given:
        def req1 = BuildResult.create('1')
        and:
        def cacheStore = applicationContext.getBean(BuildStore) as BuildStore
        // insert a value
        cacheStore.storeBuild('foo', req1)

        when: "wait for an update never will arrive"
        cacheStore.awaitBuild('foo').get()
        then:
        def e = thrown(ExecutionException)
        e.cause.class == BuildTimeoutException
    }

}
