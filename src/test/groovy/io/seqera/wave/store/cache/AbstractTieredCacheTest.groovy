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
        def l2 = applicationContext.getBean(RedisL2TieredCache)
        def cache = new MyCache(l2, Duration.ofMillis(AWAIT), 100)
        and:
        def k = UUID.randomUUID().toString()

        expect:
        cache.get(k) == null

        when:
        cache.put(k, new Entry('x','y'))
        then:
        cache.get(k) == new Entry('x','y')
        and:
        (encoder.decode(l2.get(k)).value as Entry) == new Entry('x','y')

        when:
        sleep AWAIT *2
        then:
        cache.get(k) == null
    }

}
