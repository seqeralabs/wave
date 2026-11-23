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

import java.security.MessageDigest
import java.security.SecureRandom
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.cookie.SameSite
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Signs a viewer in through the Platform that owns the record they asked for.
 *
 * Authorization code flow with PKCE: the verifier stays in Wave, so an intercepted code is
 * useless on its own.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class ViewAuthService {

    static final String COOKIE = 'wave-view-session'

    /** Holds the hash of the state this browser started a sign-in with */
    static final String STATE_COOKIE = 'wave-view-state'

    /** The claim marker outlives the entry it consumes, so the state cannot be replayed */
    static private final String CLAIMED = 'claimed/'

    private static final SecureRandom RANDOM = new SecureRandom()

    @Inject
    private PendingSignInStore pendingStore

    @Inject
    private ViewSessionStore sessionStore

    @Inject
    private OidcDiscoveryClient discoveryClient

    @Value('${wave.server.url}')
    private String serverUrl

    /** Registered per paired Platform; sign-in is unavailable for any endpoint not listed */
    @Inject
    @Nullable
    private List<ViewAuthClient> clients

    protected ViewAuthClient client(String towerEndpoint) {
        return towerEndpoint ? clients?.find { it.matches(towerEndpoint) } : null
    }

    protected String clientId(String towerEndpoint) {
        return client(towerEndpoint)?.clientId
    }

    protected String clientSecret(String towerEndpoint) {
        return client(towerEndpoint)?.clientSecret
    }

    /**
     * The state cookie, set with a lifetime and cleared with zero. Both go through here: a
     * clearing Set-Cookie only takes effect when its path and attributes match the one that set it.
     */
    Cookie stateCookie(String value, long maxAge) {
        return Cookie.of(STATE_COOKIE, value)
                .path('/view/auth')
                .httpOnly(true)
                .secure(secureCookie())
                .sameSite(SameSite.Lax)
                .maxAge(maxAge)
    }

    /** A Secure cookie is dropped over plain http, which would loop a dev viewer through sign-in */
    boolean secureCookie() {
        return serverUrl?.startsWith('https')
    }

    String callbackUri() {
        return "${serverUrl}/view/auth/callback".toString()
    }

    /**
     * @return The Platform URL to redirect the browser to, or null when that Platform cannot
     *      sign viewers in.
     */
    SignInRedirect startSignIn(String towerEndpoint, String returnTo) {
        // the client id first: it is a config lookup, and it keeps the discovery fetch - and its
        // cache - to the endpoints Wave can actually sign in to
        final id = clientId(towerEndpoint)
        if( !id )
            return null
        final discovery = discoveryClient.discover(towerEndpoint)
        if( !discovery?.usable() )
            return null

        final state = rnd()
        final verifier = rnd()
        pendingStore.put(state, new PendingSignIn(towerEndpoint: towerEndpoint, codeVerifier: verifier, returnTo: returnTo))

        final params = [
                response_type: 'code',
                client_id: id,
                redirect_uri: callbackUri(),
                scope: 'openid profile email',
                state: state,
                code_challenge: challengeOf(verifier),
                code_challenge_method: 'S256' ]
        final url = "${discovery.authorization_endpoint}?${OidcDiscoveryClient.formEncode(params)}".toString()
        return new SignInRedirect(url, challengeOf(state))
    }

    /**
     * @return the pending sign-in, or null unless this is the browser that started it and the
     *      first caller to claim the state. The claim is the atomic step: get-then-remove would
     *      let two callbacks carrying one state both through.
     */
    PendingSignIn consume(String state, String stateHash) {
        if( !state || !stateHash )
            return null
        if( !MessageDigest.isEqual(challengeOf(state).bytes, stateHash.bytes) )
            return null
        final pending = pendingStore.get(state)
        // a claim marker carries no verifier: a state spelled 'claimed/<other>' finds one
        if( !pending?.codeVerifier )
            return null
        // an empty marker: the store is typed to PendingSignIn, but this need not hold a verifier
        if( !pendingStore.putIfAbsent(CLAIMED + state, new PendingSignIn()) )
            return null
        pendingStore.remove(state)
        return pending
    }


    /** @return the new session id, or null when the exchange failed */
    String completeSignIn(PendingSignIn pending, String code, String existingSessionId) {
        final token = discoveryClient.exchangeCode(
                pending.towerEndpoint,
                clientId(pending.towerEndpoint),
                clientSecret(pending.towerEndpoint),
                callbackUri(),
                code,
                pending.codeVerifier )
        if( !token )
            return null

        // never adopt the id the browser arrived with: a planted one would end up holding this
        // viewer's token. Tokens already held for other Platforms are carried across.
        final current = existingSessionId ? sessionStore.get(existingSessionId) : null
        final sessionId = rnd()
        sessionStore.put(sessionId, (current ?: new ViewSession()).withToken(pending.towerEndpoint, token))
        if( existingSessionId )
            sessionStore.remove(existingSessionId)
        return sessionId
    }

    /** @return the viewer's session, or null. One read for a caller that needs more than the token */
    ViewSession session(String sessionId) {
        return sessionId ? sessionStore.get(sessionId) : null
    }

    /**
     * Drops a token Platform has rejected, so the next page starts a fresh sign-in.
     * Takes the session the caller already read: the store has no local tier, so re-reading
     * it here would cost a second round-trip.
     */
    void forget(String sessionId, ViewSession session, String towerEndpoint) {
        if( session?.tokenFor(towerEndpoint) )
            sessionStore.put(sessionId, session.without(towerEndpoint))
    }

    /** @return the endpoint when Wave can sign in to it, otherwise a constant - keeps metric labels bounded */
    String known(String towerEndpoint) {
        return clientId(towerEndpoint) ? towerEndpoint : 'other'
    }

    static private String rnd() {
        final buf = new byte[32]
        RANDOM.nextBytes(buf)
        return Base64.urlEncoder.withoutPadding().encodeToString(buf)
    }

    static protected String challengeOf(String verifier) {
        final hash = MessageDigest.getInstance('SHA-256').digest(verifier.getBytes('US-ASCII'))
        return Base64.urlEncoder.withoutPadding().encodeToString(hash)
    }

}
