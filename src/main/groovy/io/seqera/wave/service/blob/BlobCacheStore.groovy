package io.seqera.wave.service.blob

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.BlobConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class BlobCacheStore extends AbstractCacheStore<BlobInfo> implements BlobStore {

    @Inject
    private BlobConfig blobConfig

    BlobCacheStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<BlobInfo>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-blobcache/v1:'
    }

    @Override
    protected Duration getDuration() {
        return blobConfig.statusDuration
    }

    @Override
    Duration getTimeout() {
        return blobConfig.transferTimeout
    }

    @Override
    Duration getDelay() {
        return blobConfig.statusDelay
    }

    @Override
    BlobInfo getBlob(String key) {
        return get(key)
    }

    @Override
    boolean storeIfAbsent(String key, BlobInfo info) {
        return putIfAbsent(key, info)
    }

    @Override
    void storeBlob(String key, BlobInfo info) {
        put(key, info)
    }
}
