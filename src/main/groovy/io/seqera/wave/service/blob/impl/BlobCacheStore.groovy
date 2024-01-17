package io.seqera.wave.service.blob.impl

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.BlobStore
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Inject
/**
 * Implement a distributed store for blob cache entry.
 *
 * NOTE: This only stores blob caching *metadata* i.e. {@link BlobCacheInfo}.
 * The blob binary is stored into an object storage bucket
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class BlobCacheStore extends AbstractCacheStore<BlobCacheInfo> implements BlobStore {

    @Inject
    private BlobCacheConfig blobConfig

    BlobCacheStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<BlobCacheInfo>() {})
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
    BlobCacheInfo getBlob(String key) {
        return get(key)
    }

    @Override
    boolean storeIfAbsent(String key, BlobCacheInfo info) {
        return putIfAbsent(key, info)
    }

    @Override
    void storeBlob(String key, BlobCacheInfo info) {
        put(key, info)
    }

    @Override
    void storeBlob(String key, BlobCacheInfo info, Duration ttl) {
        put(key, info, ttl)
    }
}
