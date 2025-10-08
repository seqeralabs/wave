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

package io.seqera.wave.proxy

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.fixtures.redis.RedisTestContainer
import io.seqera.wave.configuration.ProxyCacheConfig
import io.seqera.wave.store.cache.RedisL2TieredCache
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ProxyCacheTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run('test', 'redis')
        sleep(500) // workaround to wait for Redis connection
    }

    def cleanup() {
        applicationContext.close()
    }

    def 'should cache user info response' () {
        given:
        def TTL = Duration.ofMillis(150)
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache = new ProxyCache(store, Mock(ProxyCacheConfig))
        and:
        def k = UUID.randomUUID().toString()
        def resp = new DelegateResponse(
                location: 'http://foo.com',
                statusCode: 200,
                body: new byte[] { 1,2,3 } )

        when:
        cache.put(k, resp, TTL)
        then:
        cache.get(k) == resp

        when:
        sleep TTL.toMillis()*2
        then:
        cache.get(k) == null
    }

}
