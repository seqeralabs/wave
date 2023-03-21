package io.seqera.wave.storage

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.exception.MismatchChecksumException
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import io.seqera.wave.storage.reader.ContentReader
import io.seqera.wave.storage.reader.DataContentReader
import io.seqera.wave.storage.reader.GzipContentReader
import io.seqera.wave.util.RegHelper
import jakarta.inject.Singleton

/**
 * Implements manifest cache for {@link DigestStore}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ManifestCacheStore extends AbstractCacheStore<DigestStore> implements Storage {

    final private Duration duration

    @Value('${wave.debugChecksum:false}')
    private boolean debugChecksum

    ManifestCacheStore(
            CacheProvider<String, String> provider,
            @Value('${wave.storage.cache.duration:`1h`}') Duration duration)
    {
        super(provider, new MoshiEncodeStrategy<DigestStore>() {})
        this.duration = duration
        log.info "Creating Manifests cache store ― duration=$duration"
    }

    @Override
    protected String getPrefix() {
        return "wave-blobs/v1:"
    }

    @Override
    protected Duration getDuration() {
        return duration
    }

    @Override
    Optional<DigestStore> getManifest(String path) {
        log.trace "Get manifest ==> $path"
        final result = this.get(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveManifest(String path, String manifest, String type, String digest) {
        log.trace "Save Manifest [size: ${manifest.size()}] ==> $path"
        final result = new ZippedDigestStore(manifest.getBytes(), type, digest);
        this.put(path, result)
        return result;
    }

    DigestStore saveManifest(String path, DigestStore store) {
        log.trace "Save Manifest [store] ==> $path"
        this.put(path, store)
        return store;
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        log.trace "Get Blob ==> $path"
        final result = this.get(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.trace "Save Blob [size: ${content.size()}] ==> $path"
        final result = new ZippedDigestStore(content, type, digest)
        this.put(path, result)
        return result
    }

    @Override
    DigestStore saveBlob(String path, ContentReader content, String type, String digest) {
        log.trace "Save Blob ==> $path"
        final result = new LazyDigestStore(content, type, digest)
        this.put(path, result)
        // validate checksum
        String d0
        if( debugChecksum && (content instanceof DataContentReader || content instanceof GzipContentReader) && digest!=(d0=RegHelper.digest(content.readAllBytes()))) {
            throw new MismatchChecksumException("Blob mismatch checksum - expected=$digest; computed=$d0", result)
        }
        return result
    }

}
