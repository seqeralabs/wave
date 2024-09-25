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

package io.seqera.wave.service.state.impl

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer

class RedisCacheProviderTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    @Shared
    RedisCacheProvider redisCacheProvider

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST : redisHostName,
                REDIS_PORT : redisPort
        ], 'test', 'redis')
        redisCacheProvider = applicationContext.getBean(RedisCacheProvider)
        sleep(500) // workaround to wait for Redis connection
    }

    def cleanup() {
        applicationContext.close()
    }

    def 'should get and put a key-value pair' () {
        given:
        def k = UUID.randomUUID().toString()

        expect:
        redisCacheProvider.get(k) == null

        when:
        redisCacheProvider.put(k, "hello")
        then:
        redisCacheProvider.get(k) == 'hello'
    }

    def 'should get and put a key-value pair with ttl' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        expect:
        redisCacheProvider.get(k) == null

        when:
        redisCacheProvider.put(k, "hello", Duration.ofMillis(TTL))
        then:
        redisCacheProvider.get(k) == 'hello'
        then:
        sleep(TTL *2)
        and:
        redisCacheProvider.get(k) == null
    }

    def 'should get and put only if absent' () {
        given:
        def k = UUID.randomUUID().toString()

        expect:
        redisCacheProvider.get(k) == null

        when:
        def done = redisCacheProvider.putIfAbsent(k, 'foo')
        then:
        done
        and:
        redisCacheProvider.get(k) == 'foo'

        when:
        done = redisCacheProvider.putIfAbsent(k, 'bar')
        then:
        !done
        and:
        redisCacheProvider.get(k) == 'foo'
    }

    def 'should get and put if absent with ttl' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        when:
        def done = redisCacheProvider.putIfAbsent(k, 'foo', Duration.ofMillis(TTL))
        then:
        done
        and:
        redisCacheProvider.get(k) == 'foo'

        when:
        done = redisCacheProvider.putIfAbsent(k, 'bar', Duration.ofMillis(TTL))
        then:
        !done
        and:
        redisCacheProvider.get(k) == 'foo'

        when:
        sleep(TTL *2)
        and:
        done = redisCacheProvider.putIfAbsent(k, 'bar', Duration.ofMillis(TTL))
        then:
        done
        and:
        redisCacheProvider.get(k) == 'bar'
    }

    def 'should put and remove a value' () {
        given:
        def TTL = 100
        def k = UUID.randomUUID().toString()

        when:
        redisCacheProvider.put(k, 'foo')
        then:
        redisCacheProvider.get(k) == 'foo'

        when:
        redisCacheProvider.remove(k)
        then:
        redisCacheProvider.get(k) == null
    }
}
