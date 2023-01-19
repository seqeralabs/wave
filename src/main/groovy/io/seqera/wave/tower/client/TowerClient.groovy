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

    TowerClient(HttpRetryable httpRetryable) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(httpRetryable.config().connectTimeout)
                .build()
        this.httpRetryable = httpRetryable
    }

    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, String authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        log.debug "Getting Tower user-info: $uri"
        return authorizedGetAsync(uri, authorization, UserInfoResponse)
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, String authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        return authorizedGetAsync(uri, authorization, ListCredentialsResponse)
    }

    CompletableFuture<GetCredentialsKeysResponse> fetchEncryptedCredentials(String towerEndpoint, String authorization, String credentialsId, String encryptionKey, Long workspaceId) {
        log.debug "Getting ListCredentials tower-endpoint=$towerEndpoint; auth=$authorization"
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, encryptionKey,workspaceId)
        return authorizedGetAsync(uri, authorization, GetCredentialsKeysResponse)
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
     * @param operation
     *      an operation tag useful for logging
     * @param uri
     *      the uri to get
     * @param authToken
     *      the token usef of the authorization
     * @param type
     *      the type of the model to convert into
     * @return a future of T
     */
    private <T> CompletableFuture<T> authorizedGetAsync(URI uri, String authToken, Class<T> type) {
        def request = HttpRequest.newBuilder()
                .uri(uri)
                .header('Authorization', "Bearer ${authToken}")
                .GET()
                .build()
       return httpRetryable.sendAsync(httpClient, request, HttpResponse.BodyHandlers.ofString())
                .thenApply { resp ->
                    log.trace "Tower response for request GET '${uri}' => ${resp.statusCode()}"
                    switch (resp.statusCode()) {
                        case 200:
                            return JacksonHelper.fromJson(resp.body(), type)
                        case 401:
                            throw new HttpResponseException(401,"Unauthorized")
                        default:
                            throw new HttpResponseException(resp.statusCode(), resp.body())
                    }
                }
    }

}
