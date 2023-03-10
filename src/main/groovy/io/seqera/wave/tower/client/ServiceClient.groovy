package io.seqera.wave.tower.client

import java.util.concurrent.CompletableFuture

interface ServiceClient {

    public <T> CompletableFuture<T> sendAsync(URI uri, String endpoint, String authorization, Class<T> type)

}
