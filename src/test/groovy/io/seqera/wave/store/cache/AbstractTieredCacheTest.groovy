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

package io.seqera.wave.store.cache

import spock.lang.Retry

import java.time.Duration
import java.time.Instant

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.Canonical
import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiSerializable
import io.seqera.wave.test.RedisTestContainer
import spock.lang.Shared
import spock.lang.Specification

class AbstractTieredCacheTest extends Specification implements RedisTestContainer {

    @Canonical
    static class MyBean implements MoshiSerializable {
        String foo
        String bar
    }

    @Memoized
    static MoshiEncodeStrategy encoder() {
        JsonAdapter.Factory factory = PolymorphicJsonAdapterFactory.of(MoshiSerializable.class, "@type")
                .withSubtype(AbstractTieredCache.Entry.class, AbstractTieredCache.Entry.name)
                .withSubtype(MyBean.class, MyBean.name)

        return new MoshiEncodeStrategy<AbstractTieredCache.Entry>(factory) {}
    }

    static class MyCache extends AbstractTieredCache<TieredKey, MyBean> {

        static String PREFIX = 'foo/v1'

        MyCache(L2TieredCache<String,String> l2) {
            super(l2, encoder())
        }

        @Override
        protected String getPrefix() { return PREFIX }

        @Override
        int getMaxSize() {
            return 100
        }

        @Override
        protected getName() { return 'foo' }
    }

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run('test', 'redis')
        sleep(500) // workaround to wait for Redis connection
    }

    def cleanup() {
        applicationContext.close()
    }

    def 'should get and put a key-value pair' () {
        given:
        def begin = System.currentTimeMillis()
        and:
        def TTL = Duration.ofMillis(150)
        def store = applicationContext.getBean(RedisL2TieredCache)
        def encoder = encoder()
        def cache1 = new MyCache(store)
        and:
        def k = UUID.randomUUID().toString()
        def value = new MyBean('x','y')

        expect:
        cache1.get(k) == null

        when:
        cache1.put(k, value, TTL)

        then:
        def entry1 = cache1.l1Get(k)
        and:
        entry1.expiresAt > begin
        then:
        entry1?.value == value

        then:
        def entry2 = encoder.decode(store.get(MyCache.PREFIX+':'+k))
        then:
        entry2.expiresAt > begin
        then:
        entry2?.value == value

        and:
        MyBean r1 = cache1.get(k)
        then:
        r1 == value

        when:
        MyBean r2 = encoder.decode(store.get(MyCache.PREFIX+':'+k))?.value as MyBean
        then:
        r2 == value

        when:
        sleep TTL.toMillis() *2
        then:
        cache1.get(k) == null
    }

    def 'should get and put a key-value pair /2' () {
        given:
        def TTL = Duration.ofMillis(150)
        def store = applicationContext.getBean(RedisL2TieredCache)
        def encoder = encoder()
        def cache1 = new MyCache(store)
        def cache2 = new MyCache(store)
        and:
        def k = UUID.randomUUID().toString()
        def value = new MyBean('x','y')

        expect:
        cache1.get(k) == null

        when:
        cache1.put(k, value, TTL)
        and:
        MyBean r1 = cache1.get(k)
        then:
        r1 == value
        
        when:
        MyBean r2 = encoder.decode(store.get(MyCache.PREFIX+':'+k))?.value as MyBean
        then:
        r2 == value

        when:
        MyBean r3 = cache2.get(k)
        then:
        r3 == value

        when:
        sleep TTL.toMillis() *2
        then:
        cache1.get(k) == null
    }

    def 'should get or compute a value' () {
        given:
        def TTL = Duration.ofMillis(150)
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new MyCache(store)
        def cache2 = new MyCache(store)
        and:
        def k = UUID.randomUUID().toString()

        expect:
        cache1.get(k) == null

        when:
        MyBean r1 = cache1.getOrCompute(k, (it)-> new MyBean(it+'1', it+'2'), TTL)
        then:
        r1 == new MyBean(k+'1', k+'2')

        when:
        MyBean r2 = cache2.get(k)
        then:
        r2 == new MyBean(k+'1', k+'2')

        when:
        sleep TTL.toMillis() *2
        then:
        cache2.get(k) == null
    }

    def 'should get or compute a value with condition' () {
        given:
        def TTL = Duration.ofMillis(150)
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache = new MyCache(store)
        and:
        def k1 = UUID.randomUUID().toString()
        def k2 = UUID.randomUUID().toString()

        expect:
        cache.get(k1) == null

        when:
        MyBean r1 = cache.getOrCompute(k1, (it)-> new Tuple2<MyBean,Duration>(new MyBean(it+'1', it+'2'), TTL))
        then:
        r1 == new MyBean(k1+'1', k1+'2')
        then:
        cache.get(k1) == new MyBean(k1+'1', k1+'2')

        when:
        MyBean r2 = cache.getOrCompute(k2, (it)-> new Tuple2<MyBean,Duration>(new MyBean(it+'3', it+'4'), null))
        then:
        r2 == new MyBean(k2+'3', k2+'4')
        then:
        cache.get(k2) == null
    }

    def 'should validate revalidation logic' () {
        given:
        def REVALIDATION_INTERVAL_SECS = 10
        def now = Instant.now()
        def cache = Spy(MyCache)
        cache.getCacheRevalidationInterval() >> Duration.ofSeconds(REVALIDATION_INTERVAL_SECS)

        when:
        // when expiration is past, then 'revalidate' should be true
        def expiration = now.minusSeconds(1)
        def revalidate = cache.shouldRevalidate(expiration.toEpochMilli(), now)
        then:
        0 * cache.randomRevalidate(_) >> null
        and:
        revalidate

        when:
        // when expiration is longer than the revalidation internal, then 'revalidate' is false
        expiration = now.plusSeconds(REVALIDATION_INTERVAL_SECS +1)
        revalidate = cache.shouldRevalidate(expiration.toEpochMilli(), now)
        then:
        0 * cache.randomRevalidate(_) >> null
        and:
        !revalidate

        when:
        // when expiration is less than or equal the revalidation internal, then 'revalidate' is computed randomly
        expiration = now.plusSeconds(REVALIDATION_INTERVAL_SECS)
        revalidate = cache.shouldRevalidate(expiration.toEpochMilli(), now)
        then:
        1 * cache.randomRevalidate(_) >> true
        and:
        revalidate

        when:
        // when expiration is less than or equal the revalidation internal, then 'revalidate' is computed randomly
        expiration = now.plusSeconds(REVALIDATION_INTERVAL_SECS -1)
        revalidate = cache.shouldRevalidate(expiration.toEpochMilli(), now)
        then:
        1 * cache.randomRevalidate(_) >> false
        and:
        !revalidate
    }

    def 'should validate random function' () {
        given:
        def now = Instant.now()
        def cache = Spy(MyCache)
        cache.getCacheRevalidationInterval() >> Duration.ofSeconds(10)
        expect:
        cache.randomRevalidate(0)
    }

    @Retry(count = 5)
    def 'should validate random revalidate with interval 10s' () {
        given:
        def now = Instant.now()
        def cache = Spy(MyCache)
        cache.getCacheRevalidationInterval() >> Duration.ofSeconds(10)
        expect:
        // when remaining time is approaching 0
        // the function should return true
        cache.randomRevalidate(10)  // 10 millis
        cache.randomRevalidate(100) // 100 millis
    }

    @Retry(count = 5)
    def 'should validate random revalidate with interval 300s' () {
        given:
        def now = Instant.now()
        def cache = Spy(MyCache)
        cache.getCacheRevalidationInterval() >> Duration.ofSeconds(300)
        expect:
        // when remaining time is approaching 0
        // the function should return true
        cache.randomRevalidate(10)  // 10 millis
        cache.randomRevalidate(100) // 100 millis
        cache.randomRevalidate(500) // 100 millis
    }
}
