/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing.socket

import spock.lang.Specification
import spock.lang.Unroll

import io.seqera.service.pairing.socket.msg.PairingHeartbeat
import io.seqera.service.pairing.socket.msg.PairingMessage
import io.seqera.service.pairing.socket.msg.PairingResponse
import io.seqera.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.service.pairing.socket.msg.ProxyHttpResponse

/**
 * Tests for {@link PairingMessageEncodeStrategy}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PairingMessageEncodeStrategyTest extends Specification {

    def 'should encode and decode PairingHeartbeat'() {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        def message = new PairingHeartbeat(msgId: 'heartbeat-123')

        when:
        def json = encoder.encode(message)
        def decoded = encoder.decode(json)

        then:
        decoded instanceof PairingHeartbeat
        decoded.msgId == 'heartbeat-123'
        json.contains('"@type":"PairingHeartbeat"')
    }

    def 'should encode and decode PairingResponse'() {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        def message = new PairingResponse(msgId: 'resp-456', publicKey: 'base64key', pairingId: 'pair-789')

        when:
        def json = encoder.encode(message)
        def decoded = encoder.decode(json)

        then:
        decoded instanceof PairingResponse
        decoded.msgId == 'resp-456'
        decoded.publicKey == 'base64key'
        decoded.pairingId == 'pair-789'
        json.contains('"@type":"PairingResponse"')
    }

    def 'should encode and decode ProxyHttpRequest'() {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        def message = new ProxyHttpRequest(
                msgId: 'req-001',
                method: 'POST',
                uri: '/api/credentials',
                auth: 'Bearer token123',
                body: '{"key":"value"}',
                headers: ['Content-Type': ['application/json'], 'Accept': ['application/json']]
        )

        when:
        def json = encoder.encode(message)
        def decoded = encoder.decode(json)

        then:
        decoded instanceof ProxyHttpRequest
        decoded.msgId == 'req-001'
        decoded.method == 'POST'
        decoded.uri == '/api/credentials'
        decoded.auth == 'Bearer token123'
        decoded.body == '{"key":"value"}'
        decoded.headers == ['Content-Type': ['application/json'], 'Accept': ['application/json']]
        json.contains('"@type":"ProxyHttpRequest"')
    }

    def 'should encode and decode ProxyHttpResponse'() {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        def message = new ProxyHttpResponse(
                msgId: 'resp-002',
                status: 200,
                body: '{"result":"success"}',
                headers: ['Content-Type': ['application/json']]
        )

        when:
        def json = encoder.encode(message)
        def decoded = encoder.decode(json)

        then:
        decoded instanceof ProxyHttpResponse
        decoded.msgId == 'resp-002'
        decoded.status == 200
        decoded.body == '{"result":"success"}'
        decoded.headers == ['Content-Type': ['application/json']]
        json.contains('"@type":"ProxyHttpResponse"')
    }

    @Unroll
    def 'should preserve message type through serialization - #messageType'() {
        given:
        def encoder = PairingMessageEncodeStrategy.create()

        when:
        def json = encoder.encode(message)
        def decoded = encoder.decode(json)

        then:
        decoded.class == message.class
        decoded.msgId == message.msgId

        where:
        messageType         | message
        'PairingHeartbeat'  | new PairingHeartbeat(msgId: 'hb-1')
        'PairingResponse'   | new PairingResponse(msgId: 'pr-1', publicKey: 'key', pairingId: 'id')
        'ProxyHttpRequest'  | new ProxyHttpRequest(msgId: 'req-1', method: 'GET', uri: '/test')
        'ProxyHttpResponse' | new ProxyHttpResponse(msgId: 'resp-1', status: 404, body: 'Not found')
    }

    def 'should handle null fields gracefully'() {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        def message = new ProxyHttpRequest(msgId: 'req-null', method: 'GET', uri: '/test')

        when:
        def json = encoder.encode(message)
        def decoded = encoder.decode(json)

        then:
        decoded instanceof ProxyHttpRequest
        decoded.msgId == 'req-null'
        decoded.auth == null
        decoded.body == null
        decoded.headers == null
    }

    def 'should handle empty headers map'() {
        given:
        def encoder = PairingMessageEncodeStrategy.create()
        def message = new ProxyHttpResponse(msgId: 'resp-empty', status: 204, headers: [:])

        when:
        def json = encoder.encode(message)
        def decoded = encoder.decode(json)

        then:
        decoded instanceof ProxyHttpResponse
        decoded.headers == [:]
    }
}
