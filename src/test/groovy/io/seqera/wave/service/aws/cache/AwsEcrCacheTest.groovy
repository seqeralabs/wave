/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.aws.cache

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.fixtures.redis.RedisTestContainer
import io.seqera.wave.store.cache.RedisL2TieredCache
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class AwsEcrCacheTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run('test', 'redis')
        sleep(500) // workaround to wait for Redis connection
    }

    def cleanup() {
        applicationContext.close()
    }

    def 'should cache ecr token response'() {
        given:
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new AwsEcrCache(store)
        def cache2 = new AwsEcrCache(store)
        and:
        def k = UUID.randomUUID().toString()
        def token = new AwsEcrAuthToken('token')

        when:
        cache1.put(k, token, Duration.ofSeconds(30))
        then:
        cache2.get(k) == token
    }
}
