/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.pairing

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import spock.lang.Specification

import groovy.json.JsonOutput
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import jakarta.inject.Inject
import reactor.core.publisher.Flux

/**
 * Verifies the pairing socket accepts a proxied HTTP response larger than
 * Micronaut's default 64 KiB WebSocket payload limit.
 *
 * Micronaut core 4.10.27+ applies {@code @OnMessage(maxPayloadLength)} to the
 * decompressed (permessage-deflate) message. Before that, only the compressed size
 * of each frame was bounded, so large but compressible responses went through.
 */
@MicronautTest
class PairingWebSocketPayloadTest extends Specification {

    @Inject
    EmbeddedServer server

    @ClientWebSocket
    static abstract class TestPairingClient implements AutoCloseable {
        final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>()
        volatile CloseReason closeReason
        WebSocketSession session

        @OnOpen
        void onOpen(WebSocketSession session) { this.session = session }

        @OnMessage
        void onMessage(String message) { messages.offer(message) }

        @OnClose
        void onClose(CloseReason reason) {
            closeReason = reason
            messages.offer("CLOSED:${reason.code}".toString())
        }
    }

    // JSON-like body, highly compressible like a Platform API response
    static String compressibleBody(int size) {
        final sb = new StringBuilder(size)
        int i = 0
        while( sb.length() < size )
            sb.append('{"name":"process_').append(i++ % 500).append('","status":"COMPLETED","cpus":2},')
        return sb.substring(0, size)
    }

    def 'should accept a proxy http response larger than 64 KiB' () {
        given:
        def client = server.applicationContext.createBean(WebSocketClient, server.URL)
        def uri = "/pairing/tower/token/foo?endpoint=${URLEncoder.encode('http://tower.io', 'UTF-8')}"
        def ws = Flux.from(client.connect(TestPairingClient, uri)).blockFirst()

        expect: 'the pairing response is received on open'
        ws.messages.poll(10, TimeUnit.SECONDS).contains('pairing-response')

        when: 'the remote service sends a large compressible response'
        final body = compressibleBody(bodySize)
        ws.session.sendSync(JsonOutput.toJson(['@type': 'proxy-http-response', msgId: 'large-1', status: 200, body: body]))
        and: 'then a heartbeat on the same session'
        ws.session.sendSync(JsonOutput.toJson(['@type': 'pairing-heartbeat', msgId: 'beat-1']))

        then: 'the session is still open and the heartbeat is answered'
        def reply = ws.messages.poll(10, TimeUnit.SECONDS)
        reply?.contains('pairing-heartbeat')
        ws.closeReason == null

        cleanup:
        ws?.close()
        client?.close()

        where:
        bodySize << [32 * 1024, 200 * 1024, 2 * 1024 * 1024]
    }
}
