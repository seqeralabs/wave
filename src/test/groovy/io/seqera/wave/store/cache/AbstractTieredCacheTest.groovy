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

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.test.RedisTestContainer

class AbstractTieredCacheTest extends Specification implements RedisTestContainer {

    @Canonical
    static class Entry {
        String foo
        String bar
    }

    static class MyCache extends AbstractTieredCache<Entry> {

        MyCache(L2TieredCache<String,String> l2, Duration ttl, long maxSize) {
            super(l2, ttl, maxSize)
        }

        @Override
        protected String getPrefix() { return 'foo/v1' }

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
        def AWAIT = 150
        def encoder = new MoshiEncodeStrategy<AbstractTieredCache.Payload>() {}
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new MyCache(store, Duration.ofMillis(AWAIT), 350)
        def cache2 = new MyCache(store, Duration.ofMillis(AWAIT), 350)
        and:
        def k = UUID.randomUUID().toString()
        def value = new Entry('x','y')

        expect:
        cache1.get(k) == null

        when:
        cache1.put(k, value)
        and:
        Entry r1 = cache1.get(k)
        then:
        r1 == value
        
        when:
        Entry r2 = encoder.decode(store.get('foo:'+k)).value as Entry
        then:
        r2 == value

        when:
        Entry r3 = cache2.get(k)
        then:
        r3 == value

        when:
        sleep AWAIT *2
        then:
        cache1.get(k) == null
    }

    def 'should get or compute a value' () {
        given:
        def AWAIT = 150
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new MyCache(store, Duration.ofMillis(AWAIT), 100)
        def cache2 = new MyCache(store, Duration.ofMillis(AWAIT), 100)
        and:
        def k = UUID.randomUUID().toString()

        expect:
        cache1.get(k) == null

        when:
        Entry r1 = cache1.getOrCompute(k, (it)-> new Entry(it+'1', it+'2'))
        then:
        r1 == new Entry(k+'1', k+'2')

        when:
        Entry r2 = cache2.get(k)
        then:
        r2 == new Entry(k+'1', k+'2')

        when:
        sleep AWAIT *2
        then:
        cache2.get(k) == null
    }

}
