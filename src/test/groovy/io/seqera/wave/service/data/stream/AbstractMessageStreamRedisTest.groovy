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

package io.seqera.wave.service.data.stream

import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ArrayBlockingQueue

import io.micronaut.context.ApplicationContext
import io.seqera.wave.service.data.stream.impl.RedisMessageStream
import io.seqera.wave.test.RedisTestContainer
import io.seqera.wave.util.LongRndKey

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class AbstractMessageStreamRedisTest extends Specification implements RedisTestContainer {

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

    def 'should offer and consume some messages' () {
        given:
        def id1 = "stream-${LongRndKey.rndHex()}"
        and:
        def target = context.getBean(RedisMessageStream)
        def stream = new TestStream(target)
        def queue = new ArrayBlockingQueue(10)
        and:
        stream.addConsumer(id1, { it-> queue.add(it) })

        when:
        stream.offer(id1, new TestMessage('one','two'))
        stream.offer(id1, new TestMessage('alpha','omega'))
        then:
        queue.take()==new TestMessage('one','two')
        queue.take()==new TestMessage('alpha','omega')
        
        cleanup:
        stream.close()
    }

}
