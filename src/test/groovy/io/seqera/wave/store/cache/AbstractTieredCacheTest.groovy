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

import java.time.Duration

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.Canonical
import groovy.transform.Memoized
import io.micronaut.context.ApplicationContext
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiExchange
import io.seqera.wave.test.RedisTestContainer
import spock.lang.Shared
import spock.lang.Specification

class AbstractTieredCacheTest extends Specification implements RedisTestContainer {

    @Canonical
    static class MyBean implements MoshiExchange {
        String foo
        String bar
    }

    @Memoized
    static MoshiEncodeStrategy encoder() {
        JsonAdapter.Factory factory = PolymorphicJsonAdapterFactory.of(MoshiExchange.class, "@type")
                .withSubtype(AbstractTieredCache.Entry.class, AbstractTieredCache.Entry.name)
                .withSubtype(MyBean.class, MyBean.name)

        return new MoshiEncodeStrategy<AbstractTieredCache.Entry>(factory) {}
    }

    static class MyCache extends AbstractTieredCache<TieredCacheKey, MyBean> {

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
        applicationContext = ApplicationContext.run([
                REDIS_HOST : redisHostName,
                REDIS_PORT : redisPort
        ], 'test', 'redis')
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
        def entry1 = cache1.l1GetEntry(k)
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

}
