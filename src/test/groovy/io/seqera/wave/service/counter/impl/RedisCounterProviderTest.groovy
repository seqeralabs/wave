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

    def cleanup() {
        applicationContext.close()
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

    def 'should get correct count value' () {
        when:
        redisCounterProvider.inc('build-x', 'foo', 1)
        redisCounterProvider.inc('build-x', 'foo', 1)
        redisCounterProvider.inc('metrics-x', 'foo', 1)

        then:
        redisCounterProvider.get('build-x', 'foo') == 2
        and:
        redisCounterProvider.get('metrics-x', 'foo') == 1
    }

    def 'should get correct org count' () {
        when:
        redisCounterProvider.inc('metrics/v1', 'builds/o/foo.com', 1)
        redisCounterProvider.inc('metrics/v1', 'builds/o/bar.io', 1)
        redisCounterProvider.inc('metrics/v1', 'builds/o/abc.org', 2)
        redisCounterProvider.inc('metrics/v1', 'pulls/o/foo.it', 1)
        redisCounterProvider.inc('metrics/v1', 'pulls/o/bar.es', 2)
        redisCounterProvider.inc('metrics/v1', 'pulls/o/abc.in', 3)
        redisCounterProvider.inc('metrics/v1', 'pulls/o/abc.com.au/d/2024-05-30', 1)
        redisCounterProvider.inc('metrics/v1', 'pulls/o/abc.com.au/d/2024-05-31', 1)

        then:
        redisCounterProvider.getAllMatchingEntries('metrics/v1', 'pulls/o/*') ==
                ['pulls/o/abc.in':3, 'pulls/o/bar.es':2, 'pulls/o/foo.it':1, 'pulls/o/abc.com.au/d/2024-05-30':1, 'pulls/o/abc.com.au/d/2024-05-31':1]
        and:
        redisCounterProvider.getAllMatchingEntries('metrics/v1', 'pulls/o/*/d/2024-05-30') ==
                ['pulls/o/abc.com.au/d/2024-05-30':1]
    }
}
