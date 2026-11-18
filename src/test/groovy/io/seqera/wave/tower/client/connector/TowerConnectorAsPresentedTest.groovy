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

package io.seqera.wave.tower.client.connector

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.service.pairing.socket.msg.ProxyHttpResponse

/**
 * The read path validates the token the caller presented, never a newer one from the store.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class TowerConnectorAsPresentedTest extends Specification {

    static final String EP = 'https://platform.io/api'
    static final URI URL = URI.create("$EP/user-info")

    /** Records the bearer that actually went on the wire */
    static class Probe extends TowerConnector {
        List<String> sent = []
        int status = 200
        @Override
        CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request) {
            sent << request.auth
            return CompletableFuture.completedFuture(
                    new ProxyHttpResponse(msgId: request.msgId, status: status, body: '{}'))
        }
    }

    /** the connector's collaborators are private and injected; a bare instance needs them set */
    private static <T extends TowerConnector> T wire(T connector, JwtAuthStore store) {
        [jwtAuthStore: store,
         maxAttempts: 6,
         retryBackOffBase: 3,
         retryBackOffDelay: 325,
         retryMaxDelay: Duration.ofSeconds(40),
         ioExecutor: Executors.newSingleThreadExecutor()].each { name, value ->
            final f = TowerConnector.getDeclaredField(name as String)
            f.setAccessible(true)
            f.set(connector, value)
        }
        return connector
    }

    private Probe probe(JwtAuthStore store) {
        return wire(new Probe(), store)
    }

    def 'should send the presented token, never the stored one' () {
        given: 'the store holds a newer token for this caller'
        def store = Mock(JwtAuthStore)
        def p = probe(store)
        def presented = JwtAuth.of(EP, 'stale-token')

        when:
        p.sendAsyncAsPresented(EP, URL, presented, Map).get()

        then: 'the store is never asked, so a revoked token cannot be silently upgraded'
        0 * store.refresh(_)
        p.sent == ['Bearer stale-token']
    }

    def 'should take the stored token on the submit path' () {
        given:
        def store = Mock(JwtAuthStore)
        def p = probe(store)
        def presented = JwtAuth.of(EP, 'stale-token')

        when:
        p.sendAsync(EP, URL, presented, Map).get()

        then:
        1 * store.refresh(presented) >> JwtAuth.of(EP, 'fresh-token')
        p.sent == ['Bearer fresh-token']
    }

    def 'should not retry the read path, which has a reader waiting on it' () {
        given: 'a connector whose transport always fails'
        def store = Mock(JwtAuthStore)
        def p = wire(new Probe() {
            @Override
            CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request) {
                sent << request.auth
                return CompletableFuture.failedFuture(new IOException('unreachable'))
            }
        }, store)

        when:
        p.sendAsyncAsPresented(EP, URL, JwtAuth.of(EP, 'tok'), Map).get()

        then: 'it answers after one attempt rather than working through the submit budget'
        thrown(Exception)
        p.sent.size() == 1
    }

}
