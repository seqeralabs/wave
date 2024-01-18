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

    /**
     * @return The max amount of time allowed to transfer the blob binary in the cache storage
     */
    Duration getTimeout()

    /**
     * @return The time interval every when the status of the blob transfer is checked
     */
    Duration getDelay()

    /**
     * Retrieve the blob cache info object for the given key
     *
     * @param key The unique key associate with the {@link BlobCacheInfo} object
     * @return The {z@link BlobCacheInfo} object associated with the specified key, or {@code null} otherwise
     */
    BlobCacheInfo getBlob(String key)

    /**
     * Store the blob cache info object with the specified key
     *
     * @param key The unique to be used to store the blob cache info
     * @param info The {@link BlobCacheInfo} object modelling the container blob information
     */
    void storeBlob(String key, BlobCacheInfo info)

    /**
     * Store the blob cache info object with the specified key. The object is evicted after the specified
     * duration is reached
     *
     * @param key The unique to be used to store the blob cache info
     * @param info The {@link BlobCacheInfo} object modelling the container blob information
     * @param ttl How long the object is allowed to stay in the cache
     */
    void storeBlob(String key, BlobCacheInfo info, Duration ttl)

    /**
     * Store a blob cache location only if the specified key does not exit
     *
     * @param key The key of the blob
     * @param info The {@link BlobCacheInfo} holding the blob location information
     * @return {@code true} if the {@link BlobCacheInfo} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String key, BlobCacheInfo info)

}
