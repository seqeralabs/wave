package io.seqera.wave.service.data.future


import java.util.concurrent.CompletableFuture

/**
 * Implements a {@link FutureStore} that allow handling {@link CompletableFuture} objects
 * in a distributed environment.
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureStore<K,V> {

    /**
     * Create a {@link CompletableFuture} object
     *
     * @param key The unique id associated with the future object
     * @return A {@link CompletableFuture} object holding the future result
     */
    CompletableFuture<V> create(K key)

    /**
     * Complete the {@link CompletableFuture} object with the specified key
     *
     * @param key The unique key of the future to complete
     * @param value The value to used to complete the future
     */
    void complete(K key, V value)

}
