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

package io.seqera.wave.service.counter.impl

import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RedisCounterProviderTest extends Specification implements RedisTestContainer {

    ApplicationContext applicationContext

    RedisCounterProvider redisCounterProvider

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST : redisHostName,
                REDIS_PORT : redisPort
        ], 'test', 'redis')
        redisCounterProvider = applicationContext.getBean(RedisCounterProvider)
        sleep(500) // workaround to wait for Redis connection
    }


    def 'should increment a counter value' () {
        expect:
        redisCounterProvider.inc('build-x', 'foo', 1) == 1
        redisCounterProvider.inc('build-x', 'foo', 1) == 2
        and:
        redisCounterProvider.inc('build-x', 'foo', 10) == 12
        and:
        redisCounterProvider.inc('build-x', 'foo', -12) == 0
    }

}
