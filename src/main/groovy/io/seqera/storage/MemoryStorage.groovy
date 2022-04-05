package io.seqera.storage

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

    private Cache<String,DigestByteArray> cache = CacheBuilder<String,DigestByteArray>
            .newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build()

    @Override
    Optional<DigestByteArray> getManifest(String path) {
        final result = cache.getIfPresent(path)
        result!=null ? Optional.of(result) : Optional.<DigestByteArray>empty()
    }

    @Override
    DigestByteArray saveManifest(String path, String manifest, String type, String digest) {
        log.debug "Save Manifest [size: ${manifest.size()}] ==> $path"
        final result = new DigestByteArray(manifest.getBytes(), type, digest);
        cache.put(path, result)
        return result;
    }

    @Override
    Optional<DigestByteArray> getBlob(String path) {
        final result = cache.getIfPresent(path)
        result!=null ? Optional.of(result) : Optional.<DigestByteArray>empty()
    }

    @Override
    DigestByteArray saveBlob(String path, byte[] blob, String type, String digest) {
        log.debug "Save Blob [size: ${blob.size()}] ==> $path"
        final result = new DigestByteArray(blob, type, digest);
        cache.put(path, result)
        return result
    }

}
