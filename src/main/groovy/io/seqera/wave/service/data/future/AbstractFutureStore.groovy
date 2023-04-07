package io.seqera.wave.service.data.future

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.EncodingStrategy
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
abstract class AbstractFutureStore<V> implements FutureStore<String,V> {

    private EncodingStrategy<V> encodingStrategy

    private FutureQueue<String> queue

    @Value('${wave.pairing.channel.awaitTimeout:100ms}')
    private Duration pollInterval

    AbstractFutureStore(FutureQueue<String> queue, EncodingStrategy<V> encodingStrategy) {
        this.queue = queue
        this.encodingStrategy = encodingStrategy
    }

    abstract String topic()

    abstract Duration getTimeout()

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
        return CompletableFuture<V>.supplyAsync(() -> {
            final start = System.currentTimeMillis()
            final max = getTimeout().toMillis()
            while( true ) {
                // try to poll a value
                final result = queue.poll(target)
                // if it's available decode it and return it
                if( result != null ) {
                    return (V)encodingStrategy.decode(result)
                }
                // check if still can wait or timeout
                if( System.currentTimeMillis()-start > max )
                    throw new TimeoutException("Unable to retrieve a value for key: $target")
                // sleep for a while
                sleep(pollInterval.toMillis())
            }
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
        final expiration = Duration.ofMillis(getTimeout().toMillis() * 20)
        final encoded = encodingStrategy.encode(value)
        final target = topic() + key
        // add the received value to corresponding queue
        queue.offer(target, encoded, expiration)
    }

}
