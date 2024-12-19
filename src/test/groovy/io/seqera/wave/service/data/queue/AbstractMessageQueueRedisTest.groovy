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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import io.micronaut.context.ApplicationContext
import io.seqera.wave.service.data.queue.impl.RedisMessageQueue
import io.seqera.wave.service.pairing.socket.PairingOutboundQueue
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import io.seqera.wave.test.RedisTestContainer
/**
 * Test class {@link AbstractMessageQueue} using a {@link RedisMessageQueue}
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
class AbstractMessageQueueRedisTest extends Specification implements RedisTestContainer {

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

    def 'should send and consume a request'() {
        given:
        def executor = Executors.newCachedThreadPool()
        def broker = context.getBean(RedisMessageQueue)
        def queue = new PairingOutboundQueue(broker, Duration.ofMillis(100), executor) .start()
        and:
        def result = new CompletableFuture<PairingMessage>()
        when:
        queue.registerClient('service-key-two', '123', { result.complete(it) })
        and:
        queue.offer('service-key-two', new PairingHeartbeat('xyz'))
        then:
        result.get(1,TimeUnit.SECONDS).msgId == 'xyz'

        cleanup:
        queue.close()
    }


    def 'should send and consume a request across instances'() {
        given:
        def executor = Executors.newCachedThreadPool()
        def broker1 = context.getBean(RedisMessageQueue)
        def queue1 = new PairingOutboundQueue(broker1, Duration.ofMillis(100), executor) .start()
        and:
        def broker2 = context.getBean(RedisMessageQueue)
        def queue2 = new PairingOutboundQueue(broker2, Duration.ofMillis(100), executor) .start()
        and:
        def result = new CompletableFuture<PairingMessage>()

        when:
        queue2.registerClient('service-key-three', '123', { result.complete(it) })
        and:
        queue1.offer('service-key-three', new PairingHeartbeat('123'))
        then:
        result.get(1,TimeUnit.SECONDS).msgId == '123'

        cleanup:
        queue1.close()
        queue2.close()
    }

}
