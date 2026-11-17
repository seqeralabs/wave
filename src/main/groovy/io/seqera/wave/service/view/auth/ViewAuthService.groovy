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
import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
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
    @Property(name = 'wave.view.auth.clients')
    @MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT)
    @Nullable
    private Map<String,String> clients

    protected String clientId(String towerEndpoint) {
        return clients?.get(towerEndpoint)?.tokenize('|')?.first()
    }

    protected String clientSecret(String towerEndpoint) {
        return clients?.get(towerEndpoint)?.tokenize('|')?.getAt(1)
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
    String startSignIn(String towerEndpoint, String returnTo) {
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
        return "${discovery.authorization_endpoint}?${OidcDiscoveryClient.formEncode(params)}".toString()
    }

    PendingSignIn consume(String state) {
        if( !state )
            return null
        final pending = pendingStore.get(state)
        if( pending )
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

    /** Drops a token Platform has rejected, so the next page starts a fresh sign-in */
    void forget(String sessionId, String towerEndpoint) {
        final session = sessionId ? sessionStore.get(sessionId) : null
        if( session?.tokenFor(towerEndpoint) )
            sessionStore.put(sessionId, session.without(towerEndpoint))
    }

    /** @return the endpoint when Wave can sign in to it, otherwise a constant - keeps metric labels bounded */
    String known(String towerEndpoint) {
        return clientId(towerEndpoint) ? towerEndpoint : 'other'
    }

    String tokenFor(String sessionId, String towerEndpoint) {
        return sessionId ? sessionStore.get(sessionId)?.tokenFor(towerEndpoint) : null
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
