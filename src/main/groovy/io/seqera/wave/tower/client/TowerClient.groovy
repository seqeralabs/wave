package io.seqera.wave.tower.client

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.util.HttpRetryable
import io.seqera.wave.util.JacksonHelper
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

    private static URI fetchCredentialsEndpoint(String towerEndpoint, String credentialsId, String encryptionKey,Long workspaceId) {
        def workspaceQueryParamm = workspaceId? "&workspaceId=${workspaceId}":""
        def baseUrl = StringUtils.stripEnd(towerEndpoint,'/')
        return new URI("${baseUrl}/credentials/${credentialsId}/keys?keyId=${encryptionKey}${workspaceQueryParamm}")
    }

    private static URI listCredentialsEndpoint(String towerEndpoint, Long workspaceId) {
        final query = workspaceId? "?workspaceId=${workspaceId}":""
        def baseUrl = StringUtils.stripEnd(towerEndpoint,'/')
        return new URI("${baseUrl}/credentials${query}")
    }

    private static URI userInfoEndpoint(String towerEndpoint) {
        def baseUrl = StringUtils.stripEnd(towerEndpoint,'/')
        return new URI("${baseUrl}/user-info")
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
