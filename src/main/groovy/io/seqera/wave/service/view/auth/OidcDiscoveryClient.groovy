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

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton

/**
 * Reads a Platform's OpenID Connect discovery document and redeems authorization codes.
 *
 * These are browser-facing endpoints Wave calls directly, not over the pairing channel, because
 * the viewer's browser must reach the same host.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class OidcDiscoveryClient {

    private static class Cached {
        final OidcDiscovery value
        final long expiresAt
        Cached(OidcDiscovery value, long expiresAt) {
            this.value = value
            this.expiresAt = expiresAt
        }
    }

    private final Map<String,Cached> cache = new ConcurrentHashMap<>()

    private final HttpClient http = HttpClientFactory.followRedirectsHttpClient()

    /**
     * The code exchange posts the client secret, and a 307 or 308 would resend the body to
     * whatever location the token endpoint names. This client refuses to follow it.
     */
    private final HttpClient tokenHttp = HttpClientFactory.neverRedirectsHttpClient()

    /** A failure is cached only briefly, so one transient outage cannot disable sign-in until restart */
    private static final long FAILURE_TTL_MILLIS = Duration.ofMinutes(1).toMillis()

    /** A success expires too: a Platform that moves its OIDC endpoints is picked up within a day */
    private static final long SUCCESS_TTL_MILLIS = Duration.ofHours(24).toMillis()

    static String formEncode(Map<String,?> params) {
        return params.collect { k, v -> "${k}=${URLEncoder.encode(v.toString(), 'UTF-8')}" }.join('&')
    }

    OidcDiscovery discover(String towerEndpoint) {
        if( !towerEndpoint )
            return null
        final hit = cache.get(towerEndpoint)
        if( hit != null && System.currentTimeMillis() < hit.expiresAt )
            return hit.value
        // deliberately not computeIfAbsent: it would hold the bin lock across the fetch
        final found = fetch(towerEndpoint)
        final ttl = found ? SUCCESS_TTL_MILLIS : FAILURE_TTL_MILLIS
        cache.put(towerEndpoint, new Cached(found, System.currentTimeMillis() + ttl))
        return found
    }

    /** The token POST on its own, so a test can stand in for the response, as fetch does */
    protected HttpResponse<String> post(HttpRequest request) {
        return tokenHttp.send(request, HttpResponse.BodyHandlers.ofString())
    }

    protected OidcDiscovery fetch(String towerEndpoint) {
        try {
            final uri = URI.create("${towerEndpoint}/.well-known/openid-configuration")
            final req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build()
            final resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if( resp.statusCode() != 200 ) {
                log.debug "OIDC discovery for '$towerEndpoint' returned ${resp.statusCode()}"
                return null
            }
            return JacksonHelper.fromJson(resp.body(), OidcDiscovery)
        }
        catch( Exception e ) {
            log.debug "OIDC discovery for '$towerEndpoint' failed - cause: ${e.message}"
            return null
        }
    }

    /** @return the access token, or null when the exchange was refused */
    String exchangeCode(String towerEndpoint, String clientId, String clientSecret, String redirectUri, String code, String codeVerifier) {
        final discovery = discover(towerEndpoint)
        if( !discovery?.usable() )
            return null
        try {
            final form = [
                    grant_type: 'authorization_code',
                    code: code,
                    redirect_uri: redirectUri,
                    client_id: clientId,
                    code_verifier: codeVerifier ]
            if( clientSecret )
                form.put('client_secret', clientSecret)
            final body = formEncode(form)
            final req = HttpRequest.newBuilder(URI.create(discovery.token_endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header('Content-Type', 'application/x-www-form-urlencoded')
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
            final resp = post(req)
            if( resp.statusCode() != 200 ) {
                // the OAuth error body names the misconfiguration and carries no token
                log.warn "OIDC token exchange with '$towerEndpoint' returned ${resp.statusCode()} - ${resp.body()?.take(500)}"
                return null
            }
            final token = (JacksonHelper.fromJson(resp.body(), Map) as Map)?.get('access_token') as String
            if( !token )
                log.warn "OIDC token exchange with '$towerEndpoint' returned no access_token"
            return token
        }
        catch( Exception e ) {
            log.warn "OIDC token exchange with '$towerEndpoint' failed - cause: ${e.message}"
            return null
        }
    }

}
