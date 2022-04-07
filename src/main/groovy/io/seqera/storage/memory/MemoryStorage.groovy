package io.seqera.storage.memory

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.config.StorageConfiguration
import io.seqera.storage.AbstractCacheStorage
import io.seqera.storage.DigestStore
import io.seqera.storage.Storage
import io.seqera.storage.util.LazyDigestStore
import io.seqera.storage.util.ZippedDigestStore
import jakarta.inject.Singleton
import com.google.common.cache.CacheBuilder

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Slf4j
@Singleton
@CompileStatic
class MemoryStorage extends AbstractCacheStorage {

    MemoryStorage(StorageConfiguration storageConfiguration){
        super(storageConfiguration)
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        Optional.ofNullable(cache.getIfPresent(path))
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.debug "Save Blob [size: ${content.size()}] ==> $path"
        final result = new ZippedDigestStore(content, type, digest);
        cache.put(path, result)
        return result
    }

    @Override
    DigestStore saveBlob(String path, Path content, String type, String digest) {
        log.debug "Save Blob [size: ${Files.size(content)}] ==> $path"
        final result = new LazyDigestStore(content, type, digest);
        cache.put(path, result)
        return result
    }

    @Override
    InputStream wrapInputStream(String path, InputStream inputStream, String type, String digest) {
        inputStream
    }
}
