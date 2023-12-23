package io.seqera.wave.service.blob

import java.time.Duration
/**
 * Implement a distributed store for blob cache entry.
 *
 * NOTE: This only stores blob caching *metadata* i.e. {@link BlobCacheInfo}.
 * The blob binary is stored into an object storage bucket 
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BlobStore {

    Duration getTimeout()

    Duration getDelay()

    BlobCacheInfo getBlob(String key)

    void storeBlob(String key, BlobCacheInfo info)

    void storeBlob(String key, BlobCacheInfo info, Duration ttl)

    /**
     * Store a blob location only if the specified key does not exit
     *
     * @param key The key of the blob
     * @param info The {@link BlobCacheInfo} holding the blob location information
     * @return {@code true} if the {@link BlobCacheInfo} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String key, BlobCacheInfo info)

}
