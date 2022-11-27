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
 * Implements manifest cache for {@link DigestStore}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ManifestCacheStore extends AbstractCacheStore<DigestStore> implements Storage {

    final private Duration duration

    ManifestCacheStore(
            CacheProvider<String, String> provider,
            @Value('${wave.storage.cache.duration:`1h`}') Duration duration)
    {
        super(provider, new MoshiEncodeStrategy<DigestStore>() {})
        this.duration = duration
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
    DigestStore get(String key) {
        final result = super.get(key)
        if( result!=null )
            return (DigestStore)result
        /*
         * try fallback to previous implementation
         */
        final value = getRaw("wave-blobs/v0:$key")
        if( value != null ) {
            return DigestStoreEncoder.decode(value)
        }
        return null
    }

    @Override
    Optional<DigestStore> getManifest(String path) {
        final result = this.get(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveManifest(String path, String manifest, String type, String digest) {
        log.debug "Save Manifest [size: ${manifest.size()}] ==> $path"
        final result = new ZippedDigestStore(manifest.getBytes(), type, digest);
        this.put(path, result)
        return result;
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        final result = this.get(path)
        result!=null ? Optional.of(result) : Optional.<DigestStore>empty()
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.debug "Save Blob [size: ${content.size()}] ==> $path"
        final result = new ZippedDigestStore(content, type, digest);
        this.put(path, result)
        return result
    }

    @Override
    DigestStore saveBlob(String path, ContentReader content, String type, String digest) {
        log.debug "Save Blob ==> $path"
        final result = new LazyDigestStore(content, type, digest);
        this.put(path, result)
        return result
    }
}
