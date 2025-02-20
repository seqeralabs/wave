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
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Function

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpStatus
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.ratelimit.impl.SpillwayRateLimiter
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.util.ExponentialAttempt
import io.seqera.wave.util.JacksonHelper
import io.seqera.wave.util.RegHelper
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
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

    @Value('${wave.pairing.channel.retryBackOffDelay:325}')
    private int retryBackOffDelay

    @Value('${wave.pairing.channel.retryMaxDelay:40s}')
    private Duration retryMaxDelay

    @Inject
    @Nullable
    private SpillwayRateLimiter limiter

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    private CacheLoader<JwtRefreshParams, CompletableFuture<JwtAuth>> loader = new CacheLoader<JwtRefreshParams, CompletableFuture<JwtAuth>>() {
        @Override
        CompletableFuture<JwtAuth> load(JwtRefreshParams params) throws Exception {
            return refreshJwtToken0(params.endpoint, params.auth)
        }
    }

    private AsyncLoadingCache<JwtRefreshParams, CompletableFuture<JwtAuth>> refreshCache

    @PostConstruct
    void init() {
        refreshCache = Caffeine
                .newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .executor(ioExecutor)
                .buildAsync(loader)
    }

    /** Only for testing - do not use */
    Cache<JwtRefreshParams, CompletableFuture<JwtAuth>> refreshCache0() {
        return refreshCache.synchronous()
    }

    protected ExecutorService getIoExecutor() {
        return ioExecutor
    }

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
    public <T> CompletableFuture<T> sendAsync(String endpoint, URI uri, JwtAuth auth, Class<T> type) {
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
    protected <T> CompletableFuture<T> sendAsync0(String endpoint, URI uri, JwtAuth auth, Class<T> type, int attempt0) {
        final msgId = rndHex()
        final attempt = newAttempt(attempt0)
        // note: use a local variable for the executor, otherwise it will fail to reference the `ioExecutor` in the closure
        final exec0 = this.ioExecutor
        return sendAsync1(endpoint, uri, auth, msgId, true)
                .thenCompose { resp ->
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
                            throw new HttpResponseException(resp.status, msg, body)
                    }
                }
                .exceptionallyCompose((Throwable err)-> {
                    if (err instanceof CompletionException)
                        err = err.cause
                    // check for retryable condition
                    final retryable = err instanceof IOException || err instanceof TimeoutException
                    if( retryable && attempt.canAttempt() && checkLimit(endpoint) ) {
                        final delay = attempt.delay()
                        final exec = CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS, exec0)
                        log.debug "Unable to connect '$endpoint' - cause: ${err.message ?: err}; attempt: ${attempt.current()}; await: ${delay}; msgId: ${msgId}"
                        return CompletableFuture.supplyAsync(()->sendAsync0(endpoint, uri, auth, type, attempt.current()+1), exec)
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

    protected boolean checkLimit(String endpoint) {
        if( !limiter )
            return true
        final result = limiter.acquireTimeoutCounter(endpoint)
        if( !result )
            log.warn "Endpoint '$endpoint' is exceeding the timeout counter limit"
        return result
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
    private CompletableFuture<ProxyHttpResponse> sendAsync1(String endpoint, final URI uri, final JwtAuth auth, String msgId, final boolean canRefresh) {
        // check the most updated JWT token
        final JwtAuth tokens = jwtAuthStore.refresh(auth) ?: auth
        log.trace "Tower request: GET '$uri' - can refresh=$canRefresh; msgId=$msgId; tokens=$tokens"
        // submit the request
        final request = new ProxyHttpRequest(
                msgId: msgId,
                method: HttpMethod.GET,
                uri: uri,
                auth: tokens && tokens.bearer ? "Bearer ${tokens.bearer}" : null )

        final response = sendAsync(endpoint, request)
                .thenComposeAsync({  resp ->
                    // log the response
                    if( resp.status==200 )
                        log.trace "Tower response: GET '${uri}' => ${resp}"
                    else
                        log.debug "Tower response: GET '${uri}' => ${resp}"
                    return CompletableFuture.completedFuture(resp)
                }, ioExecutor)
        // when accessing unauthorised resources, token refresh is not needed
        if( !auth )
            return response

        return response
                .thenComposeAsync({ resp ->
                    if (resp.status == 401 && tokens.refresh && canRefresh) {
                        final refreshId = rndHex()
                        return refreshJwtToken(endpoint, tokens)
                                .thenCompose((JwtAuth it) -> sendAsync1(endpoint, uri, tokens, refreshId, false))
                    } else {
                        return CompletableFuture.completedFuture(resp)
                    }
                }, ioExecutor)
    }

    /**
     * POST request to refresh the client JWT refresh token
     *
     * @param endpoint The target endpoint
     * @param auth A {@link JwtAuth} object holding the JWT access and refresh token
     * @return The refreshed {@link JwtAuth} object
     */
    protected CompletableFuture<JwtAuth> refreshJwtToken(String endpoint, JwtAuth auth) {
        return refreshCache.synchronous().get(new JwtRefreshParams(endpoint,auth))
    }

    protected CompletableFuture<JwtAuth> refreshJwtToken0(String endpoint, JwtAuth auth) {
        final body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(auth.refresh, 'UTF-8')}"
        final uri = refreshTokenEndpoint(endpoint)
        log.trace "Tower Refresh '$uri' request; auth=$auth"

        final msgId = rndHex()
        final request = new ProxyHttpRequest(
                msgId: msgId,
                method: HttpMethod.POST,
                uri: uri,
                headers: ['Content-Type': ['application/x-www-form-urlencoded']],
                body: body
        )

        return sendAsync(endpoint, request)
                .thenApplyAsync({ resp ->
                    if( resp==null )
                        throw new HttpResponseException(500, "Missing Tower response refreshing JWT token: ${request.uri}")
                    log.debug "Tower Refresh '$uri' response; msgId=${msgId}\n- status : ${resp.status}\n- headers: ${RegHelper.dumpHeaders(resp.headers)}\n- content: ${resp.body}"
                    if ( resp.status >= 400 ) {
                        final msg = "Unexpected Tower response refreshing JWT token: ${request.uri}"
                        throw new HttpResponseException(resp.status, msg, resp.body)
                    }
                    final cookies = resp.headers?['set-cookie'] ?: List.<String>of()
                    final newAuth = parseTokens(cookies, auth)
                    jwtAuthStore.store(newAuth)
                    return newAuth
                }, ioExecutor)
    }

    protected static JwtAuth parseTokens(List<String> cookies, JwtAuth auth) {
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
                return auth.withBearer(jwtToken.value).withRefresh(jwtRefresh.value)
            }
        }
        if (!jwtToken) {
            throw new HttpResponseException(412, 'Missing JWT token in Tower client response')
        }
        // this is the case where the server returned only the jwt
        // we go with the original refresh token
        return auth.withBearer(jwtToken.value)
    }

    protected static URI refreshTokenEndpoint(String towerEndpoint) {
        return URI.create("${TowerClient.checkEndpoint(towerEndpoint)}/oauth/access_token")
    }

    abstract CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request)

}
