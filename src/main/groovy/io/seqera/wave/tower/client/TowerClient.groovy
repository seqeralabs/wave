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
 * Tower API client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class TowerClient {

    private HttpClient httpClient

    private HttpRetryable httpRetryable

    private String towerProtocol



    TowerClient(HttpRetryable httpRetryable) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(httpRetryable.config().connectTimeout)
                .build()
        this.httpRetryable = httpRetryable
    }


    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, String authorization) {
        log.debug "Getting UserInfo tower-endpoint=$towerEndpoint; auth=$authorization"
        final req = HttpRequest.newBuilder()
                .uri(userInfoEndpoint(towerEndpoint))
                .headers('Content-Type', 'application/json', 'Authorization', "Bearer $authorization")
                .GET()
                .build()

        httpRetryable.sendAsync(httpClient, req, HttpResponse.BodyHandlers.ofString()).thenApply((resp)-> {
                log.debug "Tower auth response: [${resp.statusCode()}] ${resp.body()}"
                switch (resp.statusCode()) {
                    case 200:
                        return JacksonHelper.fromJson(resp.body(), UserInfoResponse)
                        break
                    case 401:
                        throw new HttpResponseException(401, "Unauthorized")
                        break
                    default:
                        throw new HttpResponseException(resp.statusCode(), resp.body())
                }
            })
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, String authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint,workspaceId)
        final req = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer ${authorization}")
                .GET()
                .build()
        return httpRetryable.sendAsync(httpClient, req, HttpResponse.BodyHandlers.ofString())
            .thenApply { resp ->
                switch (resp.statusCode()) {
                    case 200:
                        return JacksonHelper.fromJson(resp.body(),ListCredentialsResponse)
                    case 401:
                        throw new HttpResponseException(401,"Unauthorized")
                    default:
                        throw new HttpResponseException(resp.statusCode(),resp.body())
                }
            }
    }

    CompletableFuture<EncryptedCredentialsResponse> fetchEncryptedCredentials(String towerEndpoint, String authorization, String credentialsId, String encryptionKey,Long workspaceId) {
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, encryptionKey,workspaceId)
        final req = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer ${authorization}")
                .GET()
                .build()

        return httpRetryable.sendAsync(httpClient, req, HttpResponse.BodyHandlers.ofString())
            .thenApply { resp ->
                switch (resp.statusCode()) {
                    case 200:
                        return JacksonHelper.fromJson(resp.body(),EncryptedCredentialsResponse)
                    case 401:
                        throw new HttpResponseException(401,"Unauthorized")
                    default:
                        throw new HttpResponseException(resp.statusCode(),resp.body())
                }
            }
    }

    private static URI fetchCredentialsEndpoint(String towerEndpoint, String credentialsId, String encryptionKey,Long workspaceId) {
        def workspaceQueryParamm = workspaceId? "&workspaceId=${workspaceId}":""
        def baseUrl = StringUtils.stripEnd(towerEndpoint,'/')
        return new URI("${baseUrl}/credentials/${credentialsId}/keys?keyId=${encryptionKey}${workspaceQueryParamm}")
    }

    private static URI listCredentialsEndpoint(String towerEndpoint, Long workspaceId) {
        final query = workspaceId? "&workspaceId=${workspaceId}":""
        def baseUrl = StringUtils.stripEnd(towerEndpoint,'/')
        return new URI("${baseUrl}/credentials${query}")
    }

    private static URI userInfoEndpoint(String towerEndpoint) {
        def baseUrl = StringUtils.stripEnd(towerEndpoint,'/')
        return new URI("${baseUrl}/user-info")
    }


}
