package io.seqera.wave.service.builder.cache

import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutionException

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.BuildTimeoutException
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStore

import jakarta.inject.Inject
import redis.clients.jedis.Jedis
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ["test", "redis"])
@Property(name='wave.build.timeout',value = '5s')
class RedisBuildStoreTest extends Specification {

    @Inject
    ApplicationContext applicationContext

    @Value('${redis.uri}')
    String redisUrl

    Jedis jedis

    def setup() {
        jedis = new Jedis(redisUrl)
        jedis.flushAll()
    }

    def cleanup(){
        jedis.close()
    }

    def 'should get and put key values' () {
        given:
        def req1 = BuildResult.create('1')
        and:
        def cacheStore = applicationContext.getBean(BuildStore)
        
        expect:
        cacheStore.getBuild('foo') == null

        when:
        cacheStore.storeBuild('foo', req1)
        then:
        cacheStore.getBuild('foo') == req1
        and:
        jedis.get("wave-build:foo").toString()

    }

    def 'should expire entry' () {
        given:
        def req1 = BuildResult.create('1')
        and:
        def cacheStore = applicationContext.getBean(BuildStore)

        expect:
        cacheStore.getBuild('foo') == null

        when:
        cacheStore.storeBuild('foo', req1)
        then:
        cacheStore.getBuild('foo') == req1
        and:
        sleep 1000
        cacheStore.getBuild('foo') == req1

        when:
        cacheStore.storeBuild('foo', req1, Duration.ofSeconds(1))
        then:
        cacheStore.getBuild('foo') == req1
        and:
        sleep 500
        cacheStore.getBuild('foo') == req1
        and:
        sleep 1_500
        cacheStore.getBuild('foo') == null
    }

    def 'should store if absent' () {
        given:
        def req1 = BuildResult.create('1')
        def req2 = BuildResult.create('2')
        and:
        def store = applicationContext.getBean(BuildStore)

        expect:
        // the value is store because the key does not exists
        store.storeIfAbsent('foo', req1)
        and:
        // the value is return
        store.getBuild('foo') == req1
        and:
        // storing a new value fails because the key already exist
        !store.storeIfAbsent('foo', req2)
        and:
        // the previous value is returned
        store.getBuild('foo') == req1

    }

    def 'should remove a build entry' () {
        given:
        def zero = BuildResult.create('1')
        def cache = applicationContext.getBean(BuildStore)

        when:
        cache.storeBuild('foo', zero)
        then:
        cache.getBuild('foo') == zero

        when:
        cache.removeBuild('foo')
        then:
        cache.getBuild('foo') == null

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
