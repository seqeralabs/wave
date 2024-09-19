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

package io.seqera.wave.service.cache.impl

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

    def 'conditional put with current value when ke is not set'() {
        when: 'conditionally set a key that has no current value'
        def current = redisCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofSeconds(100))

        then: 'the provided value is returned'
        current == 'new-value'

        and: 'the value is set in the store'
        redisCacheProvider.get('key') == 'new-value'
    }

    def 'conditional put with current value when key is already set'() {
        given: 'a store containing a mapping for key that is not expired'
        redisCacheProvider.put('key', 'existing', Duration.ofSeconds(100))

        when: 'try to conditionally set the key to a new value'
        def current = redisCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofSeconds(100))

        then: 'the existing value is returned'
        current == 'existing'

        and: 'the value is not updated in the store'
        redisCacheProvider.get('key') == 'existing'

    }

    def 'conditional put with current value when key is set and has expired'() {
        given: 'a store containing a mapping for key that will expire'
        redisCacheProvider.put('key', 'existing', Duration.ofSeconds(1))
        // give time for redis to expire the key
        sleep(Duration.ofSeconds(2).toMillis())

        when: 'try to conditionally set the key to a new value'
        def current = redisCacheProvider.putIfAbsentAndGetCurrent('key', 'new-value', Duration.ofSeconds(100))

        then: 'the provided value is returned'
        current == 'new-value'

        and: 'the value is updated is set in the store'
        redisCacheProvider.get('key') == 'new-value'
    }

}
