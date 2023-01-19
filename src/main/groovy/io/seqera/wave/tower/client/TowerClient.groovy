package io.seqera.wave.tower.client

import groovy.transform.stc.SimpleType
import io.seqera.wave.model.TowerTokens

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.util.logging.Slf4j
import io.micronaut.http.exceptions.UriSyntaxException
import io.micronaut.http.uri.UriBuilder
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.util.HttpRetryable
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton

/**
 * Tower service client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class TowerClient {

    private HttpClient httpClient

    private HttpRetryable httpRetryable

    private CookieManager cookieManager

    TowerClient(HttpRetryable httpRetryable) {
        this.cookieManager = new CookieManager()
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(cookieManager)
                .connectTimeout(httpRetryable.config().connectTimeout)
                .build()
        this.httpRetryable = httpRetryable
    }

    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, TowerTokens authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        log.debug "Getting Tower user-info: $uri"
        return authorizedGetAsync(uri,towerEndpoint, authorization, UserInfoResponse)
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, TowerTokens authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        return authorizedGetAsync(uri,towerEndpoint, authorization, ListCredentialsResponse)
    }

    CompletableFuture<GetCredentialsKeysResponse> fetchEncryptedCredentials(String towerEndpoint, TowerTokens authorization, String credentialsId, String encryptionKey, Long workspaceId) {
        log.debug "Getting ListCredentials tower-endpoint=$towerEndpoint; auth=$authorization"
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, encryptionKey,workspaceId)
        return authorizedGetAsync(uri,towerEndpoint, authorization, GetCredentialsKeysResponse)
    }

    protected static URI fetchCredentialsEndpoint(String towerEndpoint, String credentialsId, String encryptionKey,Long workspaceId) {
        if (!credentialsId) {
            throw new IllegalArgumentException("credentialsId should not be null or empty")
        }
        if (!encryptionKey) {
            throw new IllegalArgumentException("encryptionKey should not be null or empty")
        }
        return buildValidUri(towerEndpoint) {
            it.path("/credentials")
                    .path(credentialsId)
                    .path('/keys')
                    .queryParam("keyId", encryptionKey)
            if (workspaceId != null) {
                it.queryParam("workspaceId", workspaceId)
            } else {
                it
            }

        }
    }

    protected static URI listCredentialsEndpoint(String towerEndpoint, Long workspaceId) {
        return buildValidUri(towerEndpoint) {
            it.path("/credentials")
            if (workspaceId != null) {
                it.queryParam("workspaceId", workspaceId)
            } else {
                it
            }
        }
    }

    protected static URI refreshTokenEndpoint(String towerEndpoint) {
        return buildValidUri(towerEndpoint) {
            it.path("/oauth/access_token")
        }
    }

    protected static URI userInfoEndpoint(String towerEndpoint) {
        return buildValidUri(towerEndpoint) {it.path("/user-info")}
    }


    private static URI buildValidUri(String towerEndpoint,
                                     @ClosureParams(value = SimpleType,
                                             options = "io.micronaut.http.uri.UriBuilder") Closure<UriBuilder> f) {
        if (!towerEndpoint) throw new IllegalArgumentException("towerEndpoint should not be null or empty")
        try {
            def builder = UriBuilder.of(towerEndpoint)
            def uri = f(builder).build()
            if (!(uri.getScheme() ==~ /https?/)) {
                throw new IllegalArgumentException("towerEndpoint should be a valid http or https url, got [${towerEndpoint}]")
            }
            return uri
        } catch (UriSyntaxException e) {
            throw new IllegalArgumentException("invalid url", e)
        }
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
    private <T> CompletableFuture<T> authorizedGetAsync(URI uri, String towerEndpoint, TowerTokens authorization, Class<T> type) {
        return authorizedGetAsyncWithRefresh(uri, towerEndpoint, authorization.authToken, authorization.refreshToken)
                    .thenApply { resp ->
                        log.trace "Tower response for request GET '${uri}' => ${resp.statusCode()}"
                        switch (resp.statusCode()) {
                            case 200:
                                return JacksonHelper.fromJson(resp.body(), type)
                            case 401:
                                throw new HttpResponseException(401, "Unauthorized")
                            default:
                                throw new HttpResponseException(resp.statusCode(),resp.body())
                        }
                    }
    }

    /**
     * Generic async get with authorization
     * that tries to refresh the authToken once
     * using the refresh token
     *
     * @param uri
     *      the uri to get
     * @param endpoint
     *      the tower endpoint
     * @param authToken
     *      the authorization token
     * @param refreshToken
     *      the refresh token
     * @return a future of the unparsed response
     */
    private CompletableFuture<HttpResponse<String>> authorizedGetAsyncWithRefresh(URI uri, String endpoint, String authToken, String refreshToken) {
        def request = HttpRequest.newBuilder()
                .uri(uri)
                .header('Authorization', "Bearer ${authToken}")
                .GET()
                .build()
        return httpRetryable.sendAsync(httpClient, request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose { resp ->
                        // we only try to refresh once so the second time we don't pass the refreshToken along
                        if (resp.statusCode() == 401 && refreshToken) {
                            return refreshJwtToken(endpoint, refreshToken)
                                        .thenCompose {
                                            authorizedGetAsyncWithRefresh(uri, endpoint, it, null)}
                        } else {
                            return CompletableFuture.completedFuture(resp)
                        }
                    }
    }


    /**
     * POST request to refresh the authToken
     *
     * @param towerEndpoint
     * @param refreshToken
     * @return
     */
    private CompletableFuture<String> refreshJwtToken(String towerEndpoint, String refreshToken) {
        final body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refreshToken,'UTF-8')}"
        final request = HttpRequest.newBuilder()
                                        .uri(refreshTokenEndpoint(towerEndpoint))
                                        .header('Content-Type', 'application/x-www-form-urlencoded')
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build()

        return httpRetryable.sendAsync(httpClient,request, HttpResponse.BodyHandlers.ofString())
                            .thenApply { resp ->
                                if (resp.statusCode() != 200) {
                                    throw new HttpResponseException(401, "Unauthorized")
                                }
                                final authCookie = getCookie("JWT")
                                final refreshCookie = getCookie("JWT_REFRESH_TOKEN")
                                return authCookie?.value
                            }

    }

    private HttpCookie getCookie(String name) {
        return cookieManager.cookieStore.cookies.find {it.name == name}
    }

    private static <T> HttpResponseException makeHttpError(int status,T body ) {
        return new HttpResponseException(status,"error.....")
    }

}
