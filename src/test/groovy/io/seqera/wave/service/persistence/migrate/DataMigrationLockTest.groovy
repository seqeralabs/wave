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

package io.seqera.wave.service.persistence.migrate

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer
import redis.clients.jedis.JedisPool

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Timeout(30)
class DataMigrationLockTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext context

    def setup() {
        context = ApplicationContext.run('test', 'redis')
    }

    def 'should get lock or timeout' () {
        given:
        def key = '/foo/v1'
        def connection = context.getBean(JedisPool).getResource()

        when:
        def lock1 = DataMigrationService.acquireLock(connection, key)
        then:
        lock1 != null

        when:
        def lock2 = DataMigrationService.acquireLock(connection, key, Duration.ofMillis(100))
        then:
        lock2 == null

        when:
        lock1.release()
        lock2 = DataMigrationService.acquireLock(connection, key)
        then:
        lock2 != null

        cleanup:
        connection?.close()
    }
}
