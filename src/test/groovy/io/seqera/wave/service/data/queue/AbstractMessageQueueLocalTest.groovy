/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
 * Test class {@link AbstractMessageQueue} using a {@link io.seqera.wave.service.data.queue.impl.LocalQueueBroker}
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@MicronautTest(environments = ['test'])
class AbstractMessageQueueLocalTest extends Specification {

    @Inject
    private MessageBroker<String> broker


    def 'should register and unregister consumers'() {
        given:
        def queue = new PairingOutboundQueue(broker, Duration.ofMillis(100))

        when:
        queue.registerClient('service-key', '123', {})
        then:
        queue.hasTarget('service-key')

        when:
        queue.unregisterClient('service-key', '123')
        then:
        !queue.hasTarget('service-key')

        cleanup:
        queue.close()
    }

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
        queue.markerKey('foo','bar') == 'pairing-outbound-queue/v1:foo:client=bar/marker'

        cleanup:
        queue.close()
    }



}
