package io.seqera.storage

import groovy.util.logging.Slf4j
import io.micronaut.cache.annotation.CachePut
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.caffeine.cache.RemovalCause
import io.micronaut.caffeine.cache.RemovalListener
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory
/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Slf4j
@Singleton
class MemoryStorage implements Storage {

    @Override
    Optional<DigestByteArray> getManifest(String path) {
        getManifestCacheable(path)
    }

    @Override
    DigestByteArray saveManifest(String path, String manifest, String type, String digest) {
        saveManifestCacheable(path, manifest, type, digest)
    }

    @Override
    Optional<DigestByteArray> getBlob(String path) {
        getBlobCacheable(path)
    }

    @Override
    DigestByteArray saveBlob(String path, byte[] bytes, String type, String digest) {
        saveBlobCacheable(path, bytes, type, digest)
    }

    @Cacheable(value = "cache-1h", parameters = "path")
    protected Optional<DigestByteArray> getManifestCacheable(String path) {
        Optional.empty()
    }

    @Cacheable(value = "cache-1h", parameters = "path")
    protected DigestByteArray saveManifestCacheable(String path, String manifest, String type, String digest) {
        log.info "Save Manifest $path"
        DigestByteArray digestByteArray = new DigestByteArray(manifest.getBytes(), type, digest);
        return digestByteArray;
    }

    @Cacheable(value = "cache-1h", parameters = "path")
    protected Optional<DigestByteArray> getBlobCacheable(String path) {
        Optional.empty()
    }

    @Cacheable(value = "cache-1h", parameters = "path")
    protected DigestByteArray saveBlobCacheable(String path, byte[] bytes, String type, String digest) {
        log.info "Save Blob $path"
        DigestByteArray digestByteArray = new DigestByteArray(bytes, type, digest);
        return digestByteArray;
    }

}
