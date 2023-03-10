package io.seqera.wave.tower.client

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

import io.seqera.wave.exception.HttpResponseException
import jakarta.inject.Inject
import org.apache.commons.lang.StringUtils

class TowerClient {

    @Inject
    private TowerClientHttp httpClient

    @Inject
    private TowerClientSocket socketClient

    protected <T> CompletableFuture<T> getAsync(URI uri, String towerEndpoint, String authorization, Class<T> type) {

        // Connect using websocket connection
        if( socketClient.isEndpointRegistered(towerEndpoint) )
            return socketClient.sendAsync(uri, towerEndpoint, authorization, type)

        // Fallback to public HTTP connection
        return httpClient.sendAsync(uri, towerEndpoint, authorization, type)
    }

    CompletableFuture<ServiceInfoResponse> serviceInfo(String towerEndpoint) {
        final uri = serviceInfoEndpoint(towerEndpoint)
        return getAsync(uri, null, null, ServiceInfoResponse)
    }

    CompletableFuture<UserInfoResponse> userInfo(String towerEndpoint, String authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        return getAsync(uri, towerEndpoint, authorization, UserInfoResponse)
    }

    CompletableFuture<ListCredentialsResponse> listCredentials(String towerEndpoint, String authorization, Long workspaceId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        return getAsync(uri, towerEndpoint, authorization, ListCredentialsResponse)
    }

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

    public static Throwable handleIoError(Throwable err, URI uri) {
        if (err instanceof CompletionException) {
            err = err.cause
        }
        if( err instanceof IOException ) {
            final message = "Unexpected I/O error while accessing Tower resource: $uri - cause: ${err.message ?: err}"
            err = new HttpResponseException(503, message)
        }
        return err
    }

    public static String checkEndpoint(String endpoint) {
        if( !endpoint )
            throw new IllegalArgumentException("Missing endpoint argument")
        if( !endpoint.startsWith('http://') && !endpoint.startsWith('https://') )
            throw new IllegalArgumentException("Endpoint should start with HTTP or HTTPS protocol — offending value: '$endpoint'")

        StringUtils.removeEnd(endpoint, "/")
    }

}
