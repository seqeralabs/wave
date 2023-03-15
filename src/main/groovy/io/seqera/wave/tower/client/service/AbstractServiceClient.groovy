package io.seqera.wave.tower.client.service


import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpMethod
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.util.JacksonHelper
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject

@Slf4j
@CompileStatic
abstract class AbstractServiceClient {

    @Inject
    private JwtAuthStore jwtAuthStore


    /**
     * Generic async get with authorization
     * that converts to the provided json model T
     *
     * @param uri
     *      the uri to get
     * @param towerEndpoint
     *      base tower endpoint
     * @param authorization
     *      the authorization tokens
     * @param type
     *      the type of the model to convert into
     * @return a future of T
     */
    public <T> CompletableFuture<T> sendAsync(String service, String serviceEndpoint, URI uri, String authorization, Class<T> type) {
        final result = authorization
                ? getAsync1(service, serviceEndpoint, uri, authorization, true)
                : getAsync0(service, serviceEndpoint, uri)
        return result
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
                .exceptionally { err ->
                    throw TowerClient.handleIoError(err, uri)
                }
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
    private CompletableFuture<ProxyHttpResponse> getAsync1(String service, String serviceEndpoint, final URI uri, final String accessToken, final boolean canRefresh) {
        // check the most updated JWT tokens
        final tokens = jwtAuthStore.getJwtAuth(serviceEndpoint, accessToken)
        log.trace "Tower GET '$uri' — can refresh=$canRefresh; tokens=$tokens"
        // submit the request
        final request = new ProxyHttpRequest(
                msgId: UUID.randomUUID(),
                method: HttpMethod.GET,
                uri: uri,
                bearerAuth: tokens.bearer
        )

        return sendAsync(service, serviceEndpoint, request)
                .thenCompose { resp ->
                    log.trace "Tower GET '$uri' response\n- status : ${resp.status}\n- content: ${resp.body}"
                    if (resp.status == 401 && tokens.refresh && canRefresh) {
                        return refreshJwtToken(service, serviceEndpoint, accessToken, tokens.refresh)
                                .thenCompose((JwtAuth it) -> getAsync1(service, serviceEndpoint, uri, accessToken, false))
                    } else {
                        return CompletableFuture.completedFuture(resp)
                    }
                }
    }

    private CompletableFuture<ProxyHttpResponse> getAsync0(String service, String serviceEndpoint, final URI uri) {
        log.trace "Tower GET '$uri'"
        // submit the request
        final request = new ProxyHttpRequest(
                msgId: UUID.randomUUID(),
                method: HttpMethod.GET,
                uri: uri
        )
        // when accessing unauthorised resources, use the http client directly
        // retryable logic is not needed, because those requests are not expected to have a high volume
        // (ultimately this only used by the wave pairing controller, if it fails,
        // tower itself will retry the request)
        return sendAsync(service, serviceEndpoint, request)
    }

    /**
     * POST request to refresh the authToken
     *
     * @param towerEndpoint
     * @param originalAuthToken used as a key for the token service
     * @param refreshToken
     * @return
     */
    protected CompletableFuture<JwtAuth> refreshJwtToken(String service, String serviceEndpoint, String originalAuthToken, String refreshToken) {
        final body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refreshToken, 'UTF-8')}"
        final uri = refreshTokenEndpoint(serviceEndpoint)
        log.trace "Tower Refresh '$uri'"

        final request = new ProxyHttpRequest(
                msgId: UUID.randomUUID(),
                method: HttpMethod.POST,
                uri: uri,
                headers: ['Content-Type': ['application/x-www-form-urlencoded']],
                body: body
        )

        return sendAsync(service, serviceEndpoint, request)
                .thenApply { resp ->
                    log.trace "Tower Refresh '$uri' response\n- status : ${resp.status}\n- headers: ${RegHelper.dumpHeaders(resp.headers)}\n- content: ${resp.body}"
                    if (resp.status >= 400) {
                        throw new HttpResponseException(resp.status, "Unexpected Tower response refreshing JWT token", resp.body)
                    }
                    final cookies = resp.headers.getOrDefault('Set-Cookie', List.of())
                    final jwtAuth = parseTokens(cookies, refreshToken)
                    return jwtAuthStore.putJwtAuth(serviceEndpoint, originalAuthToken, jwtAuth)
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

    abstract CompletableFuture<ProxyHttpResponse> sendAsync(String service, String endpoint, ProxyHttpRequest request)
}
