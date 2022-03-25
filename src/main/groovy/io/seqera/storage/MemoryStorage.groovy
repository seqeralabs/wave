package io.seqera.storage;

import io.micronaut.cache.annotation.CachePut;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.caffeine.cache.RemovalCause;
import io.micronaut.caffeine.cache.RemovalListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Singleton
class MemoryStorage implements Storage, RemovalListener<String, DigestByteArray> {

    private static Logger logger = LoggerFactory.getLogger(MemoryStorage.class);

    @Override
    @Cacheable(value = "manifests", parameters = "path")
    Optional<DigestByteArray> getManifest(String path) {
        return Optional.ofNullable(null);
    }

    @Override
    @CachePut(value = "manifests", parameters = "path")
    DigestByteArray saveManifest(String path, String manifest, String type, String digest) {
        DigestByteArray digestByteArray = new DigestByteArray(manifest.getBytes(), type, digest);
        return digestByteArray;
    }

    @Override
    @Cacheable(value = "blobs", parameters = "path")
    Optional<DigestByteArray> getBlob(String path) {
        println "getBlob $path"
        return Optional.ofNullable(null);
    }

    @Override
    @CachePut(value = "blobs", parameters = "path")
    DigestByteArray saveBlob(String path, byte[] bytes, String type, String digest) {
        println "saveBlob $path"
        DigestByteArray digestByteArray = new DigestByteArray(bytes, type, digest);
        return digestByteArray;
    }

    @Override
    void onRemoval(String s, DigestByteArray digestByteArray, RemovalCause removalCause) {
        logger.info("Removing {} from cache", s);
    }
}
