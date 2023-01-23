package io.seqera.wave.tower.client

import groovy.transform.stc.SimpleType

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
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.util.HttpRetryable
import io.seqera.wave.util.JacksonHelper
import io.seqera.wave.util.RegHelper
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

    private HttpRetryable httpRetryable

    private JwtAuthStore jwtAuthStore

    private HttpClient client

    TowerClient(HttpRetryable httpRetryable, JwtAuthStore jwtAuthStore) {
        this.httpRetryable = httpRetryable
        this.jwtAuthStore = jwtAuthStore
        this.client = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(httpRetryable.config().connectTimeout)
                                .build()

    }

    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, String authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        return authorizedGetAsync(uri, towerEndpoint, authorization, UserInfoResponse)
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, String authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        return authorizedGetAsync(uri,towerEndpoint, authorization, ListCredentialsResponse)
    }

    CompletableFuture<GetCredentialsKeysResponse> fetchEncryptedCredentials(String towerEndpoint, String authorization, String credentialsId, String encryptionKey, Long workspaceId) {
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, encryptionKey,workspaceId)
        return authorizedGetAsync(uri, towerEndpoint, authorization, GetCredentialsKeysResponse)
    }

    protected static URI fetchCredentialsEndpoint(String towerEndpoint, String credentialsId, String encryptionKey, Long workspaceId) {
        if( !towerEndpoint )
            throw new IllegalArgumentException("Missing towerEndpoint argument")
        if (!credentialsId)
            throw new IllegalArgumentException("Missing credentialsId argument")
        if (!encryptionKey)
            throw new IllegalArgumentException("Missing encryptionKey argument")

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
    private <T> CompletableFuture<T> authorizedGetAsync(URI uri, String towerEndpoint, String authorization, Class<T> type) {
        return authorizedGetAsyncWithRefresh(uri, towerEndpoint, authorization, true)
                    .thenCompose { resp ->
                        log.trace "Tower response for request GET '${uri}' => ${resp.statusCode()}"
                        switch (resp.statusCode()) {
                            case 200:
                                return CompletableFuture.completedFuture(JacksonHelper.fromJson(resp.body(), type))
                            case 401:
                                throw new HttpResponseException(401, 'Unauthorized')
                            case 404:
                                throw makeNotFoundError(towerEndpoint,uri)
                            default:
                                throw makeGenericError(resp.statusCode(), towerEndpoint,uri, resp.body())
                        }
                    }
    }

    private static HttpResponseException makeNotFoundError(String endpoint, URI uri) {
        final message = """
            Failed to get credentials keys from '${endpoint}', while contacting tower at: '${uri}'
            Check that you are using the correct endpoint and workspace configuration for tower and nextflow, and that the credentials are available in the tower instance.
            """.stripIndent().trim()
        return new HttpResponseException(404,message)
    }

    private static <T> HttpResponseException makeGenericError(int status, String endpoint,URI uri,  T body) {
        final message = """
            Failed to get credentials keys from '${endpoint}', while contacting tower at '${uri}'.
            Status: [${status}]
            Response from server:
            `
            ${JacksonHelper.toJson(body)}
            `
            """.stripIndent().trim()
        return new HttpResponseException(status, message)
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
    private CompletableFuture<HttpResponse<String>> authorizedGetAsyncWithRefresh(final URI uri, final String endpoint, final String accessToken, final boolean canRefresh) {
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
                        log.trace "Tower GET '$uri' response\n- status : ${resp.statusCode()}\n- headers: ${RegHelper.dumpHeaders(resp.headers())}\n- content: ${resp.body()}"
                        if (resp.statusCode() == 401 && tokens.refresh && canRefresh ) {
                            return refreshJwtToken(endpoint, accessToken, tokens.refresh)
                                        .thenCompose( (JwtAuth it)->authorizedGetAsyncWithRefresh(uri, endpoint, accessToken,false) )
                        } else {
                            return CompletableFuture.completedFuture(resp)
                        }
                    }
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
                                log.trace "Tower GET '$uri' response\n- status : ${resp.statusCode()}\n- headers: ${RegHelper.dumpHeaders(resp.headers())}\n- content: ${resp.body()}"
                                final status = resp.statusCode()
                                if ( status >= 400 ) {
                                    throw new HttpResponseException(status, "Unexpected Tower response refreshing JWT token")
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
