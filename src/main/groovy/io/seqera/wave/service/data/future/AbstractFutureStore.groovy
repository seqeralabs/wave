package io.seqera.wave.service.data.future

import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import jakarta.annotation.PreDestroy
/**
 * Implements a {@link FutureStore} that allow handling {@link CompletableFuture} objects
 * in a distributed environment.
 *
 * The main idea of this data structure is each party in a distributed partition has an instance
 * of {@link FutureStore}. Each {@link FutureStore} holds a collection of collection {@link CompletableFuture}.
 *
 * The {@link CompletableFuture} cannot be shared across the partitions, however it can be completed
 * by any party having the same {@link AbstractFutureStore#topic()}.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
abstract class AbstractFutureStore<V> implements FutureStore<String,V>, AutoCloseable {

    private EncodingStrategy<V> encodingStrategy

    private FutureQueue<String> queue

    AbstractFutureStore(FutureQueue<String> queue, EncodingStrategy<V> encodingStrategy) {
        this.encodingStrategy = encodingStrategy
        this.queue = queue
    }

    abstract String topic()

    abstract Duration timeout()

    /**
     * Create a new {@link CompletableFuture} instance. The future can be "completed" by this or any other instance
     * having the same {@link #topic()} value.
     *
     * @param key A key identifying this future in an univocal manner.
     * @return A {@link CompletableFuture} object.
     */
    @Override
    CompletableFuture<V> create(String key) {
        final target = topic() + key
        return (CompletableFuture<V>) CompletableFuture
                .supplyAsync( () -> {
                    log.debug "Entering queue pool $key"
                    final result = queue.poll(target, timeout())
                    log.trace "Received future message=$result"
                    return encodingStrategy.decode(result)
                })
    }

    /**
     * Notify the completion of a {@link CompletableFuture}. This method can be invoked by any
     * of the parties sharing the same {@link #topic()} name, irrespective where the future object
     * was created with the {@link #create(java.lang.String)}  method.
     *
     * @param key The {@link CompletableFuture} unique key.
     * @param value The value objected to be assigned to the {@link CompletableFuture} identified by the {@code key} parameter.
     */
    @Override
    void complete(String key, V value) {
        final encoded = encodingStrategy.encode(value)
        final target = topic() + key
        queue.offer(target, encoded, timeout())
    }

    /**
     * Cancel all non-completed futures nad close related resources.
     */
    @PreDestroy
    @Override
    void close() {
        queue.close()
    }
}
