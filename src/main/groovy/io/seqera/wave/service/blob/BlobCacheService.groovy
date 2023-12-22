package io.seqera.wave.service.blob

import java.util.concurrent.CompletableFuture

import io.seqera.wave.core.RoutePath

/**
 * Define a service to cache container blob layers
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BlobCacheService {

    CompletableFuture<BlobInfo> getBlobCacheURI(RoutePath route, Map<String,List<String>> headers)

}
