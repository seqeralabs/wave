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

package io.seqera.wave.service.view.auth

import spock.lang.Specification

import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class OidcDiscoveryClientTest extends Specification {

    static final String EP = 'https://platform.io/api'

    private static OidcDiscovery usable() {
        new OidcDiscovery(authorization_endpoint: "$EP/authorize", token_endpoint: "$EP/token")
    }

    def 'should take the access token from the exchange, and only when there is one' () {
        given:
        def reply = Mock(HttpResponse) { statusCode() >> status; body() >> payload }
        def client = new OidcDiscoveryClient() {
            @Override protected OidcDiscovery fetch(String endpoint) { usable() }
            @Override protected HttpResponse<String> post(HttpRequest request) { reply }
        }

        expect:
        client.exchangeCode(EP, 'wave', 'secret', 'https://wave.test/view/auth/callback', 'a-code', 'verifier') == token

        where:
        status | payload                                  || token
        200    | '{"access_token":"tok","expires_in":60}'  || 'tok'
        200    | '{"token_type":"Bearer"}'                 || null
        400    | '{"error":"invalid_grant"}'               || null
        401    | '{"error":"invalid_client"}'              || null
        500    | ''                                        || null
    }

    def 'should answer null when the token endpoint cannot be reached' () {
        given:
        def client = new OidcDiscoveryClient() {
            @Override protected OidcDiscovery fetch(String endpoint) { usable() }
            @Override protected HttpResponse<String> post(HttpRequest request) { throw new IOException('refused') }
        }

        expect: 'the viewer gets the refusal page, not a stack trace'
        client.exchangeCode(EP, 'wave', 'secret', 'https://wave.test/view/auth/callback', 'a-code', 'verifier') == null
    }

    def 'should cache a successful discovery' () {
        given:
        int calls = 0
        def client = new OidcDiscoveryClient() {
            @Override protected OidcDiscovery fetch(String endpoint) { calls++; usable() }
        }

        when:
        def a = client.discover(EP)
        def b = client.discover(EP)

        then: 'the document is fetched once and reused'
        a == usable()
        b == usable()
        calls == 1
    }

    def 'should retry a failed discovery instead of disabling sign-in until restart' () {
        given: 'the first fetch fails, later ones succeed'
        int calls = 0
        def client = new OidcDiscoveryClient() {
            @Override protected OidcDiscovery fetch(String endpoint) { calls++; calls == 1 ? null : usable() }
        }

        when: 'the failure is observed'
        def first = client.discover(EP)

        then:
        first == null
        calls == 1

        when: 'the negative entry expires'
        expire(client, EP)
        def second = client.discover(EP)

        then: 'the endpoint recovers on its own'
        second == usable()
        calls == 2
    }

    def 'should not refetch while the negative entry is live' () {
        given:
        int calls = 0
        def client = new OidcDiscoveryClient() {
            @Override protected OidcDiscovery fetch(String endpoint) { calls++; null }
        }

        when:
        client.discover(EP)
        client.discover(EP)

        then: 'a down Platform is not hammered once per sign-in'
        calls == 1
    }

    /** Ages the cached entry out, rather than sleeping for the real failure TTL */
    private static void expire(OidcDiscoveryClient client, String endpoint) {
        final f = OidcDiscoveryClient.getDeclaredField('cache')
        f.setAccessible(true)
        final cache = f.get(client) as Map
        final entry = cache.get(endpoint)
        final ef = entry.getClass().getDeclaredField('expiresAt')
        ef.setAccessible(true)
        ef.set(entry, 0L)
    }

}
