package io.seqera.wave.storage

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import io.seqera.wave.storage.reader.ContentReader
import jakarta.inject.Singleton

/**
 * Implements a base cache for {@link DigestStore} objects
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class CacheDigestStorage extends AbstractCacheStore<DigestStore> implements DigestStorage{

    private Duration maxDuration
    CacheDigestStorage(@Value('${wave.storage.cache.duration:`1h`}')Duration maxDuration,
                       CacheProvider<String, String> delegate) {
        super(delegate, new MoshiEncodeStrategy<DigestStore>(){})
        this.maxDuration = maxDuration
    }

    @Override
    protected String getPrefix() {
        return "wave-blobs/v0:"
    }

    @Override
    protected Duration getTimeout() {
        return maxDuration
    }

    @Override
    Optional<DigestStore> getManifest(String path) {
        final result = get(path) as DigestStore
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveManifest(String path, String manifest, String type, String digest) {
        log.debug "Save Manifest [size: ${manifest.size()}] ==> $path"
        final result = new ZippedDigestStore(manifest.getBytes(), type, digest);
        put(path, result)
        return result;
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        final result = get(path) as DigestStore
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.debug "Save Blob [size: ${content.size()}] ==> $path"
        final result = new ZippedDigestStore(content, type, digest)
        put(path, result)
    }

    @Override
    DigestStore saveBlob(String path, ContentReader content, String type, String digest) {
        log.debug "Save Blob ==> $path"
        final result = new LazyDigestStore(content, type, digest)
        put(path, result)
    }
}
