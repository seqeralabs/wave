package io.seqera.wave.tower.client

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
@CompileStatic
class TowerClientDelegate extends TowerClient {

    @Inject
    private TowerClientHttp httpClient

    @Inject
    private TowerClientSocket socketClient

    @Override
    protected <T> CompletableFuture<T> getAsync(URI uri, String towerEndpoint, String authorization, Class<T> type) {

        // Connect using websocket connection
        if( socketClient.isEndpointRegistered(towerEndpoint) )
            return socketClient.getAsync(uri, towerEndpoint, authorization, type)

        // Fallback to public HTTP connection
        return httpClient.getAsync(uri, towerEndpoint, authorization, type)
    }
}
