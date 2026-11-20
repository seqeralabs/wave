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

import java.security.MessageDigest

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ViewAuthServiceTest extends Specification {

    static final String EP = 'https://platform.io/api'

    private static OidcDiscovery usable() {
        new OidcDiscovery(
                issuer: EP,
                authorization_endpoint: "$EP/authorize",
                token_endpoint: "$EP/token" )
    }

    private ViewAuthService svc(OidcDiscovery discovery, Map clients, Map store=[:]) {
        def pending = Mock(PendingSignInStore) {
            put(_, _) >> { String k, PendingSignIn v -> store.put(k, v) }
            get(_) >> { String k -> store.get(k) }
            remove(_) >> { String k -> store.remove(k) }
        }
        def disco = Mock(OidcDiscoveryClient) { discover(_) >> discovery }
        return new ViewAuthService(
                pendingStore: pending,
                sessionStore: Mock(ViewSessionStore),
                discoveryClient: disco,
                serverUrl: 'https://wave.test',
                clients: clients )
    }

    def 'should build an authorization url with PKCE' () {
        given:
        def store = [:]
        def service = svc(usable(), [(EP): 'wave-client|secret'], store)

        when:
        def url = service.startSignIn(EP, '/view/builds/bd-1')

        then:
        url.startsWith("$EP/authorize?")
        url.contains('response_type=code')
        url.contains('client_id=wave-client')
        url.contains('code_challenge_method=S256')
        url.contains(URLEncoder.encode('https://wave.test/view/auth/callback', 'UTF-8'))

        and: 'the verifier is held server side, never put on the wire'
        store.size() == 1
        def pending = store.values().first()
        pending.codeVerifier
        !url.contains(pending.codeVerifier)
        pending.returnTo == '/view/builds/bd-1'

        and: 'the challenge is the SHA-256 of the verifier'
        def expected = Base64.urlEncoder.withoutPadding().encodeToString(
                MessageDigest.getInstance('SHA-256').digest(pending.codeVerifier.getBytes('US-ASCII')))
        url.contains(URLEncoder.encode(expected, 'UTF-8'))
    }

    def 'should return no url when the Platform cannot sign viewers in' () {
        expect: 'no client registered for that endpoint'
        svc(usable(), [:]).startSignIn(EP, '/view/builds/bd-1') == null
        and: 'the Platform serves no OpenID Connect endpoints'
        svc(new OidcDiscovery(issuer: EP), [(EP): 'wave-client|secret']).startSignIn(EP, '/view/builds/bd-1') == null
    }

    def 'should consume a state exactly once' () {
        given:
        def store = [:]
        def service = svc(usable(), [(EP): 'wave-client|secret'], store)
        service.startSignIn(EP, '/x')
        def state = store.keySet().first()

        expect:
        service.consume(state) != null
        and: 'a replay of the same state finds nothing'
        service.consume(state) == null
        and:
        service.consume(null) == null
        service.consume('never-issued') == null
    }

    def 'should split the client id from its secret' () {
        given:
        def service = svc(usable(), [(EP): 'the-id|the-secret'])

        expect:
        service.clientId(EP) == 'the-id'
        service.clientSecret(EP) == 'the-secret'
        and: 'a public client has no secret'
        svc(usable(), [(EP): 'only-id']).clientSecret(EP) == null
    }

}
