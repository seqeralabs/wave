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

import io.micronaut.context.ApplicationContext

import java.security.MessageDigest

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ViewAuthServiceTest extends Specification {

    static final String EP = 'https://platform.io/api'

    private static OidcDiscovery usable() {
        new OidcDiscovery(
                authorization_endpoint: "$EP/authorize",
                token_endpoint: "$EP/token" )
    }

    private static List<ViewAuthClient> entries(Map<String,String> packed) {
        return packed.collect { ep, v ->
            final c = new ViewAuthClient('n' + ep.hashCode())
            c.endpoint = ep
            c.clientId = v.tokenize('|').first()
            c.clientSecret = v.tokenize('|').getAt(1)
            return c
        }
    }

    private ViewAuthService svc(OidcDiscovery discovery, Map clients, Map store=[:], PendingSignInStore pendingOverride=null, ViewSessionStore sessions=null) {
        def pending = Mock(PendingSignInStore) {
            put(_, _) >> { String k, PendingSignIn v -> store.put(k, v) }
            get(_) >> { String k -> store.get(k) }
            remove(_) >> { String k -> store.remove(k) }
            putIfAbsent(_, _) >> { String k, PendingSignIn v -> store.containsKey(k) ? false : (store.put(k, v) == null) }
        }
        def disco = Mock(OidcDiscoveryClient) { discover(_) >> discovery }
        return new ViewAuthService(
                pendingStore: pendingOverride ?: pending,
                sessionStore: sessions ?: Mock(ViewSessionStore),
                discoveryClient: disco,
                serverUrl: 'https://wave.test',
                clients: entries(clients) )
    }

    def 'should build an authorization url with PKCE' () {
        given:
        def store = [:]
        def service = svc(usable(), [(EP): 'wave-client|secret'], store)

        when:
        def signIn = service.startSignIn(EP, '/view/builds/bd-1')
        def url = signIn.url

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

    def 'should not fetch discovery from an endpoint Wave has no client for' () {
        given:
        def disco = Mock(OidcDiscoveryClient)
        def service = new ViewAuthService(
                pendingStore: Mock(PendingSignInStore),
                sessionStore: Mock(ViewSessionStore),
                discoveryClient: disco,
                serverUrl: 'https://wave.test',
                clients: entries([:]) )

        when: 'an endpoint the record carried, which nobody proved they control'
        def signIn = service.startSignIn('http://10.0.0.5:8080/admin', '/view/builds/bd-1')

        then: 'it is never dialled'
        0 * disco.discover(_)
        signIn == null
    }

    def 'should hold one token per Platform, whatever spelling a record carries' () {
        given: 'a token stored under the spelling the signed-in record used'
        def session = new ViewSession().withToken('https://api.cloud.seqera.io/', 'tok')

        expect: 'another record of the same Platform finds it'
        session.tokenFor(spelling) == 'tok'

        where:
        spelling << [
                'https://api.cloud.seqera.io',
                'https://api.cloud.seqera.io/',
                'https://API.cloud.seqera.io',
                'https://api.cloud.seqera.io:443' ]
    }

    def 'should drop the token for a Platform however the record spells it' () {
        given:
        def session = new ViewSession().withToken('https://api.cloud.seqera.io', 'tok')

        expect:
        session.without('https://api.cloud.seqera.io:443').tokenFor('https://api.cloud.seqera.io') == null
    }

    def 'should find the client for every spelling that pairs to the same Platform' () {
        given: 'a client registered under the canonical spelling'
        def service = svc(usable(), [('https://api.cloud.seqera.io'): 'wave-client|secret'])

        expect: 'a submit naming any of these pairs, so its records must sign in too'
        service.clientId(spelling) == 'wave-client'

        where:
        spelling << [
                'https://api.cloud.seqera.io',
                'https://api.cloud.seqera.io/',
                'https://API.cloud.seqera.io',
                'https://api.cloud.seqera.io:443',
                'HTTPS://api.cloud.seqera.io' ]
    }

    def 'should name the state cookie after the sign-in it belongs to' () {
        expect: 'two private pages opened at once keep a cookie each'
        ViewAuthService.stateCookieName('aaaaaaaa11') != ViewAuthService.stateCookieName('bbbbbbbb22')
        and: 'the callback can name the cookie from the state it carries'
        ViewAuthService.stateCookieName('aaaaaaaa11') == "${ViewAuthService.STATE_COOKIE}-aaaaaaaa"
        and: 'no state falls back to the plain name, so a stray callback still clears something'
        ViewAuthService.stateCookieName(null) == ViewAuthService.STATE_COOKIE
    }

    def 'should return no url when the Platform cannot sign viewers in' () {
        expect: 'no client registered for that endpoint'
        svc(usable(), [:]).startSignIn(EP, '/view/builds/bd-1') == null
        and: 'the Platform serves no OpenID Connect endpoints'
        svc(new OidcDiscovery(), [(EP): 'wave-client|secret']).startSignIn(EP, '/view/builds/bd-1') == null
    }

    def 'should consume a state exactly once, and only for the browser that started it' () {
        given:
        def store = [:]
        def service = svc(usable(), [(EP): 'wave-client|secret'], store)
        def signIn = service.startSignIn(EP, '/x')
        def state = store.keySet().first()

        expect: 'the cookie this browser was given'
        service.consume(state, signIn.stateHash) != null
        and: 'a replay finds nothing'
        service.consume(state, signIn.stateHash) == null

        when: 'a second sign-in, whose state is planted on a browser holding the first hash'
        def other = svc(usable(), [(EP): 'wave-client|secret'], store)
        other.startSignIn(EP, '/y')
        def planted = (store.keySet() - [state]).find { !it.startsWith('claimed/') }

        then: 'the state does not hash to the cookie, so it is refused'
        other.consume(planted, signIn.stateHash) == null
        and: 'and with no cookie at all'
        other.consume(planted, null) == null

        and: 'the usual misses'
        service.consume(null, 'anything') == null
        service.consume('never-issued', 'anything') == null
    }

    def 'should let only one of two racing callbacks through' () {
        given: 'a store whose remove has not taken effect yet, so both callers still read the state'
        def kept = [:]
        def pending = Mock(PendingSignInStore) {
            put(_, _) >> { String k, PendingSignIn v -> kept.put(k, v) }
            get(_) >> { String k -> kept.get(k) }
            remove(_) >> { }   // the window the claim exists to close
            putIfAbsent(_, _) >> { String k, PendingSignIn v -> kept.containsKey(k) ? false : (kept.put(k, v) == null) }
        }
        def service = new ViewAuthService(
                pendingStore: pending,
                sessionStore: Mock(ViewSessionStore),
                discoveryClient: Mock(OidcDiscoveryClient) { discover(_) >> usable() },
                serverUrl: 'https://wave.test',
                clients: entries([(EP): 'wave-client|secret']) )
        def signIn = service.startSignIn(EP, '/x')
        def state = kept.keySet().first()

        expect: 'the first caller claims it'
        service.consume(state, signIn.stateHash) != null
        and: 'the second is refused even though the entry is still there'
        service.consume(state, signIn.stateHash) == null
    }

    def 'should never adopt the session id the browser arrived with' () {
        given: 'a viewer already signed in to another Platform'
        def sessions = [:]
        def sessionStore = Mock(ViewSessionStore) {
            get(_) >> { String k -> sessions.get(k) }
            put(_, _) >> { String k, ViewSession v -> sessions.put(k, v) }
            remove(_) >> { String k -> sessions.remove(k) }
        }
        sessions.put('old-session', new ViewSession().withToken('https://other.io/api', 'other-tok'))
        and:
        def service = new ViewAuthService(
                pendingStore: Mock(PendingSignInStore),
                sessionStore: sessionStore,
                discoveryClient: Mock(OidcDiscoveryClient) {
                    discover(_) >> usable()
                    exchangeCode(_, _, _, _, _, _) >> 'new-tok' },
                serverUrl: 'https://wave.test',
                clients: entries([(EP): 'wave-client|secret']) )

        when:
        def id = service.completeSignIn(new PendingSignIn(towerEndpoint: EP, codeVerifier: 'v', returnTo: '/x'), 'code', 'old-session')

        then: 'a planted id is never reused, so it cannot end up holding this viewer\'s token'
        id != 'old-session'
        and: 'the old session is dropped'
        sessions.get('old-session') == null
        and: 'the new one holds the new token and carries the other Platform across'
        sessions.get(id).tokenFor(EP) == 'new-tok'
        sessions.get(id).tokenFor('https://other.io/api') == 'other-tok'
    }

    def 'should bind the clients from flat properties, as an env var supplies them' () {
        given: 'the flat form, which is what WAVE_VIEW_AUTH_CLIENTS_CLOUD_CLIENT_SECRET resolves to'
        def ctx = ApplicationContext.run(
                'wave.view.auth.clients.cloud.endpoint': 'https://api.cloud.seqera.io',
                'wave.view.auth.clients.cloud.client-id': 'the-id',
                // a secret containing the separator the packed form used to split on
                'wave.view.auth.clients.cloud.client-secret': 'a|b|c' )

        when:
        def found = ctx.getBeansOfType(ViewAuthClient).toList()

        then:
        found.size() == 1
        found[0].clientId == 'the-id'
        found[0].clientSecret == 'a|b|c'
        and: 'a trailing slash in either spelling still matches'
        found[0].matches('https://api.cloud.seqera.io')
        found[0].matches('https://api.cloud.seqera.io/')

        cleanup:
        ctx.close()
    }

    def 'should not hand back a claim marker as a pending sign-in' () {
        given: 'a consumed state, whose claim marker is still in the store'
        def store = [:]
        def service = svc(usable(), [(EP): 'wave-client|secret'], store)
        def signIn = service.startSignIn(EP, '/x')
        def state = store.keySet().first()
        service.consume(state, signIn.stateHash)
        def marker = store.keySet().find { it.startsWith('claimed/') }

        expect: 'the marker is there to be found'
        marker

        when: 'the marker key is presented as a state, with a cookie that hashes it'
        def hash = ViewAuthService.challengeOf(marker)

        then: 'it carries no verifier, so it is not a sign-in anyone can complete'
        service.consume(marker, hash) == null
    }

}
