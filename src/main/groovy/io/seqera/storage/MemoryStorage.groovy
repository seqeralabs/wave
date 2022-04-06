package io.seqera.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import com.google.common.cache.Cache
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import jakarta.inject.Singleton
import com.google.common.cache.CacheBuilder

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Slf4j
@Singleton
@CompileStatic
class MemoryStorage implements Storage {

    private Cache<String,DigestStore> cache = CacheBuilder<String,DigestStore>
            .newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build()

    @Override
    Optional<DigestStore> getManifest(String path) {
        final result = cache.getIfPresent(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveManifest(String path, String manifest, String type, String digest) {
        log.debug "Save Manifest [size: ${manifest.size()}] ==> $path"
        final result = new ZippedDigestStore(manifest.getBytes(), type, digest);
        cache.put(path, result)
        return result;
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        final result = cache.getIfPresent(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
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
}
