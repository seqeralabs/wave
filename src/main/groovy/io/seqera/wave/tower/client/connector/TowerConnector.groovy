/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Function

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.util.ExponentialAttempt
import io.seqera.wave.util.JacksonHelper
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
import static io.seqera.wave.util.LongRndKey.rndHex
/**
 * Implements an abstract client that allows to connect Tower service either
 * via HTTP client or a WebSocket-based client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@CompileStatic
abstract class TowerConnector {

    @Inject
    private JwtAuthStore jwtAuthStore

    @Value('${wave.pairing.channel.maxAttempts:6}')
    private int maxAttempts

    @Value('${wave.pairing.channel.retryBackOffBase:3}')
    private int retryBackOffBase

    @Value('${wave.pairing.channel.retryBackOffDelay:250}')
    private int retryBackOffDelay

    @Value('${wave.pairing.channel.retryMaxDelay:30s}')
    private Duration retryMaxDelay

    /**
     * Generic async get with authorization
     * that converts to the provided json model T
     *
     * @param uri
     *      the uri to get
     * @param towerEndpoint
     *      base tower endpoint
     * @param auth
     *      the authorization token
     * @param type
     *      the type of the model to convert into
     * @return a future of T
     */
    public <T> CompletableFuture<T> sendAsync(String endpoint, URI uri, String auth, Class<T> type) {
        return sendAsync0(endpoint, uri, auth, type, 1)
    }

    protected ExponentialAttempt newAttempt(int attempt) {
        new ExponentialAttempt()
                .builder()
                .withAttempt(attempt)
                .withMaxDelay(retryMaxDelay)
                .withBackOffBase(retryBackOffBase)
                .withBackOffDelay(retryBackOffDelay)
                .withMaxAttempts(maxAttempts)
                .build()
    }

    @CompileDynamic
    protected <T> CompletableFuture<T> sendAsync0(String endpoint, URI uri, String authorization, Class<T> type, int attempt0) {
        final msgId = rndHex()
        final attempt = newAttempt(attempt0)
        return sendAsync1(endpoint, uri, authorization, msgId, true)
                .thenCompose { resp ->
                    log.trace "Tower response for request GET '${uri}' => ${resp.status}"
                    switch (resp.status) {
                        case 200:
                            return CompletableFuture.completedFuture(JacksonHelper.fromJson(resp.body, type))
                        case 401:
                            throw new HttpResponseException(401, "Unauthorized access to Tower resource: $uri", resp.body)
                        case 404:
                            final msg = "Tower resource not found: $uri"
                            throw new HttpResponseException(404, msg, resp.body)
                        default:
                            def body = resp.body
                            def msg = "Unexpected status code ${resp.status} while accessing Tower resource: $uri"
                            if (body)
                                msg += " - response: ${body}"
                            throw new HttpResponseException(resp.status, msg)
                    }
                }
                .exceptionallyCompose((Throwable err)-> {
                    if (err instanceof CompletionException)
                        err = err.cause
                    // check for retryable condition
                    final retryable = err instanceof IOException || err instanceof TimeoutException
                    if( retryable && attempt.canAttempt() ) {
                        final delay = attempt.delay()
                        final exec = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
                        log.debug "Unable to connect '$endpoint' - cause: ${err.message ?: err}; attempt: ${attempt.current()}; await: ${delay}; msgId: ${msgId}"
                        return CompletableFuture.supplyAsync(()->sendAsync0(endpoint, uri, authorization, type, attempt.current()+1), exec)
                                .thenCompose(Function.identity());
                    }
                    // report IO error
                    if( err instanceof IOException ) {
                        final message = "Unexpected I/O error while accessing Tower resource: $uri - cause: ${err.message ?: err}"
                        err = new HttpResponseException(HttpStatus.SERVICE_UNAVAILABLE, message)
                    }
                    if( err instanceof TimeoutException ) {
                        final message = "Timeout error connecting to '$endpoint'"
                        err = new HttpResponseException(HttpStatus.REQUEST_TIMEOUT, message, err)
                    }
                    throw err
                })
    }

    /**
     * Generic async get with authorization
     * that tries to refresh the authToken once
     * using the refresh token
     *
     * @param uri
     *      The uri to get
     * @param endpoint
     *      The tower endpoint
     * @param accessToken
     *      The authorization token provided in the original request. This can be updated overtime
     * @param canRefresh
     *      Whenever the access token can be refreshed if the authorization fails
     * @return
     *      A future of the unparsed response
     */
    private CompletableFuture<ProxyHttpResponse> sendAsync1(String endpoint, final URI uri, final String accessToken, String msgId, final boolean canRefresh) {
        // check the most updated JWT tokens
        final JwtAuth tokens = accessToken ? jwtAuthStore.getJwtAuth(endpoint, accessToken) : null
        log.trace "Tower GET '$uri' - can refresh=$canRefresh; msgId=$msgId; tokens=$tokens"
        // submit the request
        final request = new ProxyHttpRequest(
                msgId: msgId,
                method: HttpMethod.GET,
                uri: uri,
                auth: tokens && tokens.bearer ? "Bearer ${tokens.bearer}" : null
        )

        final response = sendAsync(endpoint, request)
        // when accessing unauthorised resources, refresh token is not needed
        if( !accessToken )
            return response

        return response
                .thenCompose { resp ->
                    log.trace "Tower GET '$uri' response => msgId:$msgId; status: ${resp.status}; content: ${resp.body}"
                    if (resp.status == 401 && tokens.refresh && canRefresh) {
                        final refreshId = rndHex()
                        return refreshJwtToken(endpoint, accessToken, tokens.refresh)
                                .thenCompose((JwtAuth it) -> sendAsync1(endpoint, uri, accessToken, refreshId, false))
                    } else {
                        return CompletableFuture.completedFuture(resp)
                    }
                }
    }

    /**
     * POST request to refresh the authToken
     *
     * @param endpoint
     * @param originalAuthToken used as a key for the token service
     * @param refreshToken
     * @return
     */
    protected CompletableFuture<JwtAuth> refreshJwtToken(String endpoint, String originalAuthToken, String refreshToken) {
        final body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refreshToken, 'UTF-8')}"
        final uri = refreshTokenEndpoint(endpoint)
        log.trace "Tower Refresh '$uri'"

        final msgId = rndHex()
        final request = new ProxyHttpRequest(
                msgId: msgId,
                method: HttpMethod.POST,
                uri: uri,
                headers: ['Content-Type': ['application/x-www-form-urlencoded']],
                body: body
        )

        return sendAsync(endpoint, request)
                .thenApply { resp ->
                    log.trace "Tower Refresh '$uri' response; msgId=${msgId}\n- status : ${resp.status}\n- headers: ${RegHelper.dumpHeaders(resp.headers)}\n- content: ${resp.body}"
                    if ( !resp || resp.status >= 400 ) {
                        def msg = "Unexpected Tower response refreshing JWT token"
                        if( resp ) msg += " [${resp.status}]"
                        throw new HttpResponseException(resp.status, msg, resp.body)
                    }
                    final cookies = resp.headers?['set-cookie'] ?: []
                    final jwtAuth = parseTokens(cookies, refreshToken)
                    return jwtAuthStore.putJwtAuth(endpoint, originalAuthToken, jwtAuth)
                }

    }

    protected static JwtAuth parseTokens(List<String> cookies, String refreshToken) {
        HttpCookie jwtToken = null
        HttpCookie jwtRefresh = null
        for (String cookie : cookies) {
            final cookieList = HttpCookie.parse(cookie)
            // pick the jwt if not done already
            jwtToken ?= cookieList.find { HttpCookie it -> it.name == 'JWT' }
            // pick the jwt_refresh if not done already
            jwtRefresh ?= cookieList.find { HttpCookie it -> it.name == 'JWT_REFRESH_TOKEN' }
            // if we have both short-circuit
            if (jwtToken && jwtRefresh) {
                return new JwtAuth(jwtToken.value, jwtRefresh.value)
            }
        }
        if (!jwtToken) {
            throw new HttpResponseException(412, 'Missing JWT token in Tower client response')
        }
        // this is the case where the server returned only the jwt
        // we go with the original refresh token
        return new JwtAuth(jwtToken.value, jwtRefresh ? jwtRefresh.value : refreshToken)
    }

    protected static URI refreshTokenEndpoint(String towerEndpoint) {
        return URI.create("${TowerClient.checkEndpoint(towerEndpoint)}/oauth/access_token")
    }

    abstract CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request)

}
