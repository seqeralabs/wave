package io.seqera.wave.service.blob

import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic

/**
 * Implement a distributed store for blob cache entry.
 *
 * NOTE: This only stores blob caching *metadata* i.e. {@link BlobInfo}.
 * The blob binary is stored into an object storage bucket 
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BlobStore {

    Duration getTimeout()

    Duration getDelay()

    BlobInfo getBlob(String key)

    void storeBlob(String key, BlobInfo info)

    void storeBlob(String key, BlobInfo info, Duration ttl)

    /**
     * Store a blob location only if the specified key does not exit
     *
     * @param key The key of the blob
     * @param info The {@link BlobInfo} holding the blob location information
     * @return {@code true} if the {@link BlobInfo} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String key, BlobInfo info)

    /**
     * Await for the container layer blob download
     *
     * @param key
     *      The container blob unique key
     * @return
     *      the {@link CompletableFuture} holding the {@link BlobInfo} associated with
     *      specified blob key or {@code null} if no blob record is associated for the
     *      given key
     */
    default BlobInfo awaitDownload(String key) {
        final result = getBlob(key)
        return result ? Waiter.awaitCompletion(this,key,result) : null
    }

    /**
     * Implement waiter common logic
     */
    @CompileStatic
    private static class Waiter {

        static BlobInfo awaitCompletion(BlobStore store, String key, BlobInfo current) {
            final beg = System.currentTimeMillis()
            // add 10% delay gap to prevent race condition with timeout expiration
            final max = (store.timeout.toMillis() * 1.10) as long
            while( true ) {
                if( current==null ) {
                    return BlobInfo.unknown()
                }

                // check is completed
                if( current.done() ) {
                    return current
                }
                // check if it's timed out
                final delta = System.currentTimeMillis()-beg
                if( delta > max )
                    throw new DownloadTimeoutException("Blob download '$key' timed out")
                // sleep a bit
                Thread.sleep(store.delay.toMillis())
                // fetch the build status again
                current = store.getBlob(key)
            }
        }
    }
}
