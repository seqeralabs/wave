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

package io.seqera.wave.service.scan

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScanIdStoreRedisTest extends Specification implements RedisTestContainer {

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

    def 'should get scan id' () {
        given:
        def store = applicationContext.getBean(ScanIdStore)
        def _100ms = 100
        def image = 'ubuntu'

        when:
        def ret1 = store.putIfAbsentAndCount(image, ScanId.of(image), Duration.ofMillis(_100ms))
        then:
        ret1.succeed
        ret1.value.toString() == 'sc-f4fb059823db8f4d_1'
        ret1.count == 1

        when:
        def ret2 = store.putIfAbsentAndCount(image, ScanId.of(image), Duration.ofMillis(_100ms))
        then:
        !ret2.succeed
        ret2.value.toString() == 'sc-f4fb059823db8f4d_1'
        ret1.count == 1

        when:
        sleep 2 *_100ms
        def ret3 = store.putIfAbsentAndCount(image, ScanId.of(image), Duration.ofMillis(_100ms))
        then:
        ret3.succeed
        ret3.value.toString() == 'sc-f4fb059823db8f4d_2'
        ret3.count == 2

    }

}
