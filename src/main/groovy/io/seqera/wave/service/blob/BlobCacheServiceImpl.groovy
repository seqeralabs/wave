package io.seqera.wave.service.blob

import java.time.Instant
import java.util.concurrent.CompletableFuture

import io.seqera.wave.core.RoutePath
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements cache for container image layer blobs
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class BlobCacheServiceImpl implements BlobCacheService {

    @Inject
    private BlobStore blobStore

    @Override
    CompletableFuture<URI> getBlobCacheURI(RoutePath routePath) {
        final info = new BlobInfo(Instant.now())
        final key = routePath.targetPath
        if( blobStore.storeIfAbsent(key, info) ) {
            // start download and caching job
            downloadAsync(key)
        }

        return blobInfo(key)
                .thenApply(it-> new URI(it.locationUri))
    }

    @Override
    CompletableFuture<BlobInfo> blobInfo(String key) {
        return blobStore
                .awaitDownload(key)
    }

    protected void downloadAsync(String key) {


    }

}
