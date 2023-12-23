package io.seqera.wave.service.blob

import javax.annotation.concurrent.ThreadSafe

import io.seqera.wave.core.RoutePath
/**
 * Defines a caching service for container layer blob object.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ThreadSafe
interface BlobCacheService {

    /**
     * Store a container blob into the cache storage that allows fast retrieval
     * via HTTP content delivery network, and retuns a {@link BlobCacheInfo} object
     * holding the HTTP download URI.
     *
     * Note this method is thread safe is expected to be thread safe across multiple replicas.
     *
     * When two cache requests are submitted nearly at the same time, the first request carries out
     * the storing in the cache operation. The second request blobs awaiting for the storing in the
     * cache to be completed and eventually returns the same {@link BlobCacheInfo} holding the cache
     * information.
     *
     * @param route The HTTP request of a container layer blob
     * @param headers The HTTP headers of a container layer blob
     * @return
     */
    BlobCacheInfo retrieveBlobCache(RoutePath route, Map<String,List<String>> headers)

}
