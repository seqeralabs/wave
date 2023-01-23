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

    private TowerAuthTokensService authTokensService

    private HttpClient client

    TowerClient(HttpRetryable httpRetryable, TowerAuthTokensService authTokensService) {
        this.httpRetryable = httpRetryable
        this.authTokensService = authTokensService
        this.client = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .connectTimeout(httpRetryable.config().connectTimeout)
                                .build()

    }

    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, String authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        log.debug "Getting Tower user-info: $uri"
        return authorizedGetAsync(uri, towerEndpoint, authorization, UserInfoResponse)
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, String authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        return authorizedGetAsync(uri,towerEndpoint, authorization, ListCredentialsResponse)
    }

    CompletableFuture<GetCredentialsKeysResponse> fetchEncryptedCredentials(String towerEndpoint, String authorization, String credentialsId, String encryptionKey, Long workspaceId) {
        log.debug "Getting ListCredentials tower-endpoint=$towerEndpoint; auth=$authorization"
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, encryptionKey,workspaceId)
        return authorizedGetAsync(uri, towerEndpoint, authorization, GetCredentialsKeysResponse)
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
     *      the uri to get
     * @param endpoint
     *      the tower endpoint
     * @param authToken
     *      the authorization token
     * @param refreshToken
     *      the refresh token
     * @return a future of the unparsed response
     */
    private CompletableFuture<HttpResponse<String>> authorizedGetAsyncWithRefresh(URI uri, String endpoint, String authorization, boolean refresh) {
        log.debug "Tower GET '$uri';  (can refresh token=$refresh)"
        final tokens = authTokensService.getTokens(endpoint, authorization)
        log.debug "Tower GET '$uri';  (can refresh token=$refresh; tokens=$tokens)"
        def request = HttpRequest.newBuilder()
                .uri(uri)
                .header('Authorization', "Bearer ${tokens.authToken}")
                .GET()
                .build()
        return httpRetryable.sendAsync(client, request, HttpResponse.BodyHandlers.ofString())
                    .thenCompose { resp ->
                        log.debug "Tower GET '$uri' response: [${resp.statusCode()}] ${resp.body()}"
                        if (resp.statusCode() == 401 && tokens.refreshToken && refresh) {
                            return refreshJwtToken(endpoint,authorization, tokens.refreshToken)
                                        .thenCompose {
                                            authorizedGetAsyncWithRefresh(uri, endpoint, authorization,false)}
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
    private CompletableFuture<TowerTokens> refreshJwtToken(String towerEndpoint, String originalAuthToken, String refreshToken) {
        log.debug "Tower refreshing access token '$towerEndpoint'"
        final body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refreshToken,'UTF-8')}"
        final request = HttpRequest.newBuilder()
                                        .uri(refreshTokenEndpoint(towerEndpoint))
                                        .header('Content-Type', 'application/x-www-form-urlencoded')
                                        .POST(HttpRequest.BodyPublishers.ofString(body))
                                        .build()

        return httpRetryable.sendAsync(client,request, HttpResponse.BodyHandlers.ofString())
                            .thenApply { resp ->
                                log.debug "Tower refresh response: [${resp.statusCode()}] ${resp.body()} - headers: ${RegHelper.dumpHeaders(resp.headers())}"
                                if (resp.statusCode() != 200) {
                                    throw new HttpResponseException(401, "Unauthorized")
                                }
                                def cookies = resp.headers().allValues('Set-Cookie')
                                def freshTokens = parseTokens(cookies,refreshToken)
                                return authTokensService.refreshTokens(towerEndpoint,originalAuthToken,freshTokens)
                            }

    }

    private static TowerTokens parseTokens(List<String> cookies, String refreshToken) {
        HttpCookie jwt = null
        HttpCookie jwtRefresh = null
        for (String cookie: cookies) {
            def cookieList = HttpCookie.parse(cookie)
            // pick the jwt if not done already
            jwt = jwt?:cookieList.find {it.name == 'JWT'}
            // pick the jwt_refresh if not done already
            jwtRefresh = jwtRefresh?:cookieList.find {it.name == 'JWT_REFRESH_TOKEN'}
            // if we have both short-circuit
            if (jwt && jwtRefresh) {
                return new TowerTokens(authToken: jwt.value, refreshToken: jwtRefresh.value)
            }
        }
        if (!jwt) {
            log.warn('No tokens received from tower')
            throw new HttpResponseException(401,'Unauthorized')
        }
        // this is the case where the server returned only the jwt
        // we go with the original refresh token
        return new TowerTokens(authToken: jwt.value, refreshToken: jwtRefresh? jwtRefresh.value: refreshToken)
    }

}
