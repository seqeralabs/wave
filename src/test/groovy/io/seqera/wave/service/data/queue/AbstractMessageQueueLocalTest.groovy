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

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.pairing.socket.PairingOutboundQueue
import io.seqera.wave.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Inject
/**
 * Test class {@link AbstractMessageQueue} using a {@link io.seqera.wave.service.data.queue.impl.LocalMessageQueue}
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@MicronautTest(environments = ['test'])
class AbstractMessageQueueLocalTest extends Specification {

    @Inject
    private MessageQueue<String> broker

    def 'should send and consume a request'() {
        given:
        def queue = new PairingOutboundQueue(broker, Duration.ofMillis(100))

        when:
        def result = new CompletableFuture<PairingMessage>()
        queue.registerClient('service-key', '123', { result.complete(it) })
        and:
        queue.offer('service-key', new PairingHeartbeat('msg-1'))
        then:
        result.get(1, TimeUnit.SECONDS).msgId == 'msg-1'

        cleanup:
        queue.close()
    }

    def 'should validate '() {
        given:
        def queue = new PairingOutboundQueue(broker, Duration.ofMillis(100))

        expect:
        queue.targetKey('foo') == 'pairing-outbound-queue/v1:foo'
        queue.clientKey('foo','bar') == 'pairing-outbound-queue/v1:foo:client=bar/queue'

        cleanup:
        queue.close()
    }



}
