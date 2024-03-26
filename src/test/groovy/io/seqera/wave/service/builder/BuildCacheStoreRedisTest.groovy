/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.builder

import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutionException

import io.micronaut.context.ApplicationContext
import io.seqera.wave.exception.BuildTimeoutException
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.Jedis
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildCacheStoreRedisTest extends Specification implements RedisTestContainer {

    ApplicationContext applicationContext

    Jedis jedis

    def setup() {
        applicationContext = ApplicationContext.run([
                wave:[ build:[ timeout: '5s' ]],
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
        jedis = new Jedis(redisHostName, redisPort as int)
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
            req1 = BuildResult.completed('1', 0, '', Instant.now(), null)
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
