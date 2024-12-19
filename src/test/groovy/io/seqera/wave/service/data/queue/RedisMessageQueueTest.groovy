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

package io.seqera.wave.service.data.queue

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.service.data.queue.impl.RedisMessageQueue
import io.seqera.wave.test.RedisTestContainer
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RedisMessageQueueTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext context

    def setup() {
        context = ApplicationContext.run([
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
    }

    def cleanup() {
        context.stop()
    }

    def 'should return null if empty' () {
        given:
        def broker = context.getBean(RedisMessageQueue)

        expect:
        broker.poll('foo') == null

        when:
        def start = System.currentTimeMillis()
        and:
        broker.poll('foo', Duration.ofMillis(500)) == null
        and:
        def delta = System.currentTimeMillis()-start
        then:
        assert delta>500
        assert delta<1000
    }

    def 'should offer and poll a value' () {
        given:
        def broker = context.getBean(RedisMessageQueue)
        and:
        broker.offer('bar', 'alpha')
        broker.offer('bar', 'beta')

        expect:
        broker.poll('foo') == null
        broker.poll('bar') == 'alpha'
        broker.poll('bar') == 'beta'
    }

    def 'should offer and poll a value after wait' () {
        given:
        def broker = context.getBean(RedisMessageQueue)
        def wait = Duration.ofMillis(500)
        and:
        broker.offer('bar1', 'alpha1')
        broker.offer('bar1', 'beta1')

        expect:
        broker.poll('foo1', wait) == null
        broker.poll('bar1', wait) == 'alpha1'
        broker.poll('bar1', wait) == 'beta1'
    }
}
