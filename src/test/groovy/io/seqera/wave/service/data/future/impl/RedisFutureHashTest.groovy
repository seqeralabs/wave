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

package io.seqera.wave.service.data.future.impl

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.test.RedisTestContainer

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RedisFutureHashTest extends Specification implements RedisTestContainer  {

    @Shared
    ApplicationContext context

    def setup() {
        context = ApplicationContext.run('test', 'redis')
    }

    def cleanup() {
        context.stop()
    }

    def 'should set and get a value' () {
        given:
        def queue = context.getBean(RedisFutureHash)

        expect:
        queue.take('xyz') == null

        when:
        queue.put('xyz', 'hello', Duration.ofSeconds(5))
        then:
        queue.take('xyz') == 'hello'
        and:
        queue.take('xyz') == null
    }

    def 'should validate expiration' () {
        given:
        def uid = UUID.randomUUID().toString()
        def queue = context.getBean(RedisFutureHash)

        when:
        queue.put(uid, 'foo', Duration.ofMillis(500))
        then:
        queue.take(uid) == 'foo'

        when:
        queue.put(uid, 'bar', Duration.ofMillis(100))
        and:
        sleep 500
        then:
        queue.take(uid) == null
    }
}
