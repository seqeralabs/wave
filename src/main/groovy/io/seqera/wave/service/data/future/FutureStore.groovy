package io.seqera.wave.service.data.future

import java.util.concurrent.CompletableFuture

/**
 * Implements a {@link FutureStore} that allow handling {@link CompletableFuture} objects
 * in a distributed environment.
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureStore<K,V> {

    CompletableFuture<V> create(K key)

    void complete(K key, V value)

}
