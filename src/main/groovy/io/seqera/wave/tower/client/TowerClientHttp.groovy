package io.seqera.wave.tower.client

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.util.HttpRetryable
import io.seqera.wave.util.JacksonHelper
import io.seqera.wave.util.RegHelper
import jakarta.inject.Singleton
import org.apache.commons.lang.StringUtils

/**
 * Tower service client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class TowerClientHttp extends TowerClient {

    private HttpRetryable httpRetryable

    private JwtAuthStore jwtAuthStore

    private HttpClient client

    TowerClientHttp(HttpRetryable httpRetryable, JwtAuthStore jwtAuthStore) {
        this.httpRetryable = httpRetryable
        this.jwtAuthStore = jwtAuthStore
        this.client = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(httpRetryable.config().connectTimeout)
                                .build()
    }

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
    protected  <T> CompletableFuture<T> getAsync(URI uri, String towerEndpoint, String authorization, Class<T> type) {
        final result = authorization
                ? getAsync1(uri, towerEndpoint, authorization, true)
                : getAsync0(uri)
        return result
                    .thenCompose { resp ->
                        log.trace "Tower response for request GET '${uri}' => ${resp.statusCode()}"
                        switch (resp.statusCode()) {
                            case 200:
                                return CompletableFuture.completedFuture(JacksonHelper.fromJson(resp.body(), type))
                            case 401:
                                throw new HttpResponseException(401, "Unauthorized access to Tower resource: $uri", resp.body())
                            case 404:
                                final msg = "Tower resource not found: $uri"
                                throw new HttpResponseException(404, msg, resp.body())
                            default:
                                def body = resp.body()
                                def msg = "Unexpected status code ${resp.statusCode()} while accessing Tower resource: $uri"
                                if( body )
                                    msg += " - response: ${body}"
                                throw new HttpResponseException(resp.statusCode(), msg)
                        }
                    }
                    .exceptionally {err ->
                        throw handleIoError(err, uri)
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
    private CompletableFuture<HttpResponse<String>> getAsync1(final URI uri, final String endpoint, final String accessToken, final boolean canRefresh) {
        // check the most updated JWT tokens
        final tokens = jwtAuthStore.getJwtAuth(endpoint, accessToken)
        log.trace "Tower GET '$uri' — can refresh=$canRefresh; tokens=$tokens"
        // submit the request
        final request = HttpRequest.newBuilder()
                .uri(uri)
                .header('Authorization', "Bearer ${tokens.bearer}")
                .GET()
                .build()

        return httpRetryable.sendAsync(client, request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose { resp ->
                        log.trace "Tower GET '$uri' response\n- status : ${resp.statusCode()}\n- content: ${resp.body()}"
                        if (resp.statusCode() == 401 && tokens.refresh && canRefresh ) {
                            return refreshJwtToken(endpoint, accessToken, tokens.refresh)
                                        .thenCompose( (JwtAuth it)->getAsync1(uri, endpoint, accessToken,false) )
                        } else {
                            return CompletableFuture.completedFuture(resp)
                        }
                    }
    }

    private CompletableFuture<HttpResponse<String>> getAsync0(final URI uri) {
        log.trace "Tower GET '$uri'"
        // submit the request
        final request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build()
        // when accessing unauthorised resources, use the http client directly
        // retryable logic is not needed, because those requests are not expected to have a high volume
        // (ultimately this only used by the wave pairing controller, if it fails,
        // tower itself will retry the request)
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * POST request to refresh the authToken
     *
     * @param towerEndpoint
     * @param originalAuthToken used as a key for the token service
     * @param refreshToken
     * @return
     */
    private CompletableFuture<JwtAuth> refreshJwtToken(String towerEndpoint, String originalAuthToken, String refreshToken) {
        final body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refreshToken,'UTF-8')}"
        final uri = refreshTokenEndpoint(towerEndpoint)
        log.trace "Tower Refresh '$uri'"
        final request = HttpRequest.newBuilder()
                                        .uri(uri)
                                        .header('Content-Type', 'application/x-www-form-urlencoded')
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build()

        return httpRetryable.sendAsync(client,request, HttpResponse.BodyHandlers.ofString())
                            .thenApply { resp ->
                                log.trace "Tower Refresh '$uri' response\n- status : ${resp.statusCode()}\n- headers: ${RegHelper.dumpHeaders(resp.headers())}\n- content: ${resp.body()}"
                                final status = resp.statusCode()
                                if ( status >= 400 ) {
                                    throw new HttpResponseException(status, "Unexpected Tower response refreshing JWT token", resp.body())
                                }
                                final cookies = resp.headers().allValues('Set-Cookie')
                                final jwtAuth = parseTokens(cookies, refreshToken)
                                return jwtAuthStore.putJwtAuth(towerEndpoint, originalAuthToken, jwtAuth)
                            }

    }

    protected static JwtAuth parseTokens(List<String> cookies, String refreshToken) {
        HttpCookie jwtToken = null
        HttpCookie jwtRefresh = null
        for (String cookie: cookies) {
            final cookieList = HttpCookie.parse(cookie)
            // pick the jwt if not done already
            jwtToken ?= cookieList.find { HttpCookie it -> it.name == 'JWT'}
            // pick the jwt_refresh if not done already
            jwtRefresh ?= cookieList.find { HttpCookie it -> it.name == 'JWT_REFRESH_TOKEN'}
            // if we have both short-circuit
            if (jwtToken && jwtRefresh) {
                return new JwtAuth(jwtToken.value, jwtRefresh.value)
            }
        }
        if (!jwtToken) {
            throw new HttpResponseException(412,'Missing JWT token in Tower client response')
        }
        // this is the case where the server returned only the jwt
        // we go with the original refresh token
        return new JwtAuth(jwtToken.value, jwtRefresh ? jwtRefresh.value: refreshToken)
    }

}
