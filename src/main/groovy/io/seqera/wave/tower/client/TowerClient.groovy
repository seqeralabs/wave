package io.seqera.wave.tower.client

import groovy.transform.stc.SimpleType
import io.seqera.wave.model.TowerTokens

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
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

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tower service client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class TowerClient {

    private HttpRetryable httpRetryable

    private Executor executor

    private TowerAuthTokensService authTokensService


    TowerClient(HttpRetryable httpRetryable, TowerAuthTokensService authTokensService) {
        this.httpRetryable = httpRetryable
        this.authTokensService = authTokensService
        this.executor = createHttpExecutor()
    }

    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, String authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        log.debug "Getting Tower user-info: $uri"
        final tokens = authTokensService.getTokens(towerEndpoint, authorization)
        return authorizedGetAsync(buildClient(), uri, towerEndpoint, tokens, UserInfoResponse)
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, String authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        final tokens = authTokensService.getTokens(towerEndpoint, authorization)
        return authorizedGetAsync(buildClient(), uri,towerEndpoint, tokens, ListCredentialsResponse)
    }

    CompletableFuture<GetCredentialsKeysResponse> fetchEncryptedCredentials(String towerEndpoint, String authorization, String credentialsId, String encryptionKey, Long workspaceId) {
        log.debug "Getting ListCredentials tower-endpoint=$towerEndpoint; auth=$authorization"
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, encryptionKey,workspaceId)
        final tokens = authTokensService.getTokens(towerEndpoint, authorization)
        return authorizedGetAsync(buildClient(), uri, towerEndpoint, tokens, GetCredentialsKeysResponse)
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
    private <T> CompletableFuture<T> authorizedGetAsync(Client httpClient,URI uri, String towerEndpoint, TowerTokens authorization, Class<T> type) {
        return authorizedGetAsyncWithRefresh(httpClient, uri, towerEndpoint, authorization, true)
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
    private CompletableFuture<HttpResponse<String>> authorizedGetAsyncWithRefresh(Client httpClient,URI uri, String endpoint, TowerTokens tokens, boolean refresh) {
        def request = HttpRequest.newBuilder()
                .uri(uri)
                .header('Authorization', "Bearer ${tokens.authToken}")
                .GET()
                .build()
        return httpRetryable.sendAsync(httpClient.httpClient, request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose { resp ->
                        if (resp.statusCode() == 401 && tokens.refreshToken && refresh) {
                            return refreshJwtToken(httpClient, endpoint, tokens)
                                        .thenCompose {
                                            authorizedGetAsyncWithRefresh(httpClient,uri, endpoint, it,false)}
                        } else {
                            return CompletableFuture.completedFuture(resp)
                        }
                    }
    }

    /**
     * Provides a new
     * client for each request
     *
     * @return {@link Client}
     */
    private Client buildClient() {
        return Client.build(executor,httpRetryable.config().connectTimeout)
    }


    /**
     * POST request to refresh the authToken
     *
     * @param towerEndpoint
     * @param refreshToken
     * @return
     */
    private CompletableFuture<TowerTokens> refreshJwtToken(Client httpClient, String towerEndpoint, TowerTokens tokens) {
        final body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(tokens.refreshToken,'UTF-8')}"
        final request = HttpRequest.newBuilder()
                                        .uri(refreshTokenEndpoint(towerEndpoint))
                                        .header('Content-Type', 'application/x-www-form-urlencoded')
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build()

        return httpRetryable.sendAsync(httpClient.httpClient,request, HttpResponse.BodyHandlers.ofString())
                            .thenApply { resp ->
                                if (resp.statusCode() != 200) {
                                    throw new HttpResponseException(401, "Unauthorized")
                                }
                                final authToken = httpClient.getCookieValue("JWT")
                                final refreshToken = httpClient.getCookieValue("JWT_REFRESH_TOKEN")
                                final freshTokens = new TowerTokens(authToken: authToken, refreshToken: refreshToken, tokenKey: tokens.tokenKey)
                                return authTokensService.refreshTokens(towerEndpoint,freshTokens)
                            }

    }

    private static <T> HttpResponseException makeHttpError(int status,T body ) {
        return new HttpResponseException(status,"error.....")
    }


    /**
     * Creates a cached thread pool similar to the default http client executor
     * @return
     */
    private static Executor createHttpExecutor() {
        final ids = new AtomicInteger()
        return Executors.newCachedThreadPool {
            final name = "tower-client-worker-${ids.getAndIncrement()}"
            final thread = new Thread(null, it, name,0, false)
            thread.setDaemon(true)
            return thread
        }
    }

    /**
     * Wrapper of HttpClient and CookieManager
     * built for each request to isolate them
     * so that nothing is persisted between them
     */
    private static class Client {
        HttpClient httpClient
        CookieManager cookieManager


        private static Client build(Executor executor, Duration connectTimeout) {
            final cookieManager = new CookieManager()
            final httpClient = HttpClient.newBuilder()
                    .executor(executor)
                    .version(HttpClient.Version.HTTP_1_1)
                    .cookieHandler(new CookieManager())
                    .connectTimeout(connectTimeout)
                    .build()
            return new Client(httpClient: httpClient, cookieManager: cookieManager)
        }

        private String getCookieValue(String name) {
            return cookieManager.cookieStore.cookies.find { it.name == name}?.value
        }
    }

}
