package io.seqera.wave.tower.client

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import io.micronaut.cache.annotation.Cacheable
import io.seqera.wave.tower.client.connector.HttpTowerConnector
import io.seqera.wave.tower.client.connector.WebSocketTowerConnector
import jakarta.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.commons.lang.StringUtils

/**
 * Implement a client to interact with Tower services
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Singleton
@CompileStatic
class TowerClient {

    @Inject
    private HttpTowerConnector httpClient

    @Inject
    private WebSocketTowerConnector socketClient

    protected <T> CompletableFuture<T> getAsync(URI uri, String endpoint, @Nullable String authorization, Class<T> type) {
        assert uri, "Missing uri argument"
        assert endpoint, "Missing endpoint argument"

        // Connect using websocket connection when available
        if( socketClient.isEndpointRegistered(endpoint) )
            return socketClient.sendAsync(endpoint, uri, authorization, type)

        // Fallback to public HTTP connection
        return httpClient.sendAsync(endpoint, uri, authorization, type)
    }

    @Cacheable('cache-20sec')
    CompletableFuture<ServiceInfoResponse> serviceInfo(String towerEndpoint) {
        final uri = serviceInfoEndpoint(towerEndpoint)
        return getAsync(uri, towerEndpoint, null, ServiceInfoResponse)
    }

    @Cacheable('cache-20sec')
    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, String authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        return getAsync(uri, towerEndpoint, authorization, UserInfoResponse)
    }

    @Cacheable('cache-20sec')
    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, String authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        return getAsync(uri, towerEndpoint, authorization, ListCredentialsResponse)
    }

    @Cacheable('cache-20sec')
    CompletableFuture<GetCredentialsKeysResponse> fetchEncryptedCredentials(String towerEndpoint, String authorization, String credentialsId, String pairingId, Long workspaceId) {
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, pairingId, workspaceId)
        return getAsync(uri, towerEndpoint, authorization, GetCredentialsKeysResponse)
    }

    protected static URI fetchCredentialsEndpoint(String towerEndpoint, String credentialsId, String pairingId, Long workspaceId) {
        if( !towerEndpoint )
            throw new IllegalArgumentException("Missing towerEndpoint argument")
        if (!credentialsId)
            throw new IllegalArgumentException("Missing credentialsId argument")
        if (!pairingId)
            throw new IllegalArgumentException("Missing encryptionKey argument")

        // keep `keyId` only for backward compatibility
        // it should be removed in a following version in favour of `pairingId`
        def uri = "${checkEndpoint(towerEndpoint)}/credentials/$credentialsId/keys?pairingId=$pairingId&keyId=$pairingId"
        if( workspaceId!=null )
            uri += "&workspaceId=$workspaceId"

        return URI.create(uri)
    }

    protected static URI listCredentialsEndpoint(String towerEndpoint, Long workspaceId) {
        def uri = "${checkEndpoint(towerEndpoint)}/credentials"
        if( workspaceId!=null )
            uri += "?workspaceId=$workspaceId"
        return URI.create(uri)
    }

    protected static URI userInfoEndpoint(String towerEndpoint) {
        return URI.create("${checkEndpoint(towerEndpoint)}/user-info")
    }

    protected static URI serviceInfoEndpoint(String towerEndpoint) {
        return URI.create("${checkEndpoint(towerEndpoint)}/service-info")
    }

    static String checkEndpoint(String endpoint) {
        if( !endpoint )
            throw new IllegalArgumentException("Missing endpoint argument")
        if( !endpoint.startsWith('http://') && !endpoint.startsWith('https://') )
            throw new IllegalArgumentException("Endpoint should start with HTTP or HTTPS protocol — offending value: '$endpoint'")

        StringUtils.removeEnd(endpoint, "/")
    }

}
