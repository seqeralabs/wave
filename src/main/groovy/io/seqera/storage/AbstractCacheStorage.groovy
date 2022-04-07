package io.seqera.storage

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import groovy.util.logging.Slf4j
import io.seqera.config.StorageConfiguration
import io.seqera.storage.util.ZippedDigestStore


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
abstract class AbstractCacheStorage implements Storage{

    protected Cache<String, DigestStore> cache

    protected AbstractCacheStorage(StorageConfiguration storageConfiguration){
        cache = CacheBuilder<String,DigestStore>
                .newBuilder()
                .maximumSize(storageConfiguration.maximumSize)
                .expireAfterAccess(storageConfiguration.expireAfter.toMinutes(), TimeUnit.MINUTES)
                .build()
    }

    @Override
    boolean containsManifest(String path) {
        getManifest(path).present
    }

    @Override
    Optional<DigestStore> getManifest(String path) {
        Optional.ofNullable(cache.getIfPresent(path))
    }

    @Override
    DigestStore saveManifest(String path, String manifest, String type, String digest) {
        log.debug "Save Manifest [size: ${manifest.size()}] ==> $path"
        final result = new ZippedDigestStore(manifest.getBytes(), type, digest);
        cache.put(path, result)
        return result;
    }

    @Override
    boolean containsBlob(String path) {
        getBlob(path).present
    }

    @Override
    abstract Optional<DigestStore> getBlob(String path)

    @Override
    abstract DigestStore saveBlob(String path, byte[] content, String type, String digest)

    @Override
    abstract DigestStore saveBlob(String path, Path content, String type, String digest)

    @Override
    abstract void asyncSaveBlob(String path, InputStream inputStream, String type, String digest)

}
