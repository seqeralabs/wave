package io.seqera.wave.service.data.future

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

import com.squareup.moshi.Types
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.util.TypeHelper
import jakarta.annotation.PreDestroy

/**
 * Implements a {@link FutureStore} that allow handling {@link CompletableFuture} objects
 * in a distributed environment.
 *
 * The main idea of this data structure is each party in a distributed partition has an instance
 * of {@link FutureStore}. Each {@link FutureStore} holds a collection of collection {@link CompletableFuture}.
 *
 * The {@link CompletableFuture} cannot be shared across the partitions, however it can be completed
 * by any party having the same {@link AbstractFutureStore#group()}.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
abstract class AbstractFutureStore<V> implements FutureStore<String,V>, FutureListener<String>, AutoCloseable {

    private Duration ttl

    private ConcurrentHashMap<String,CompletableFuture<V>> allFutures = new ConcurrentHashMap<>()

    private EncodingStrategy<FutureEntry<String,V>> encodingStrategy

    private FuturePublisher<String> publisher

    AbstractFutureStore(FuturePublisher<String> publisher, Duration timeout) {
        final type = TypeHelper.getGenericType(this, 0)
        this.ttl = timeout
        this.encodingStrategy = new MoshiEncodeStrategy<FutureEntry<String, V>>(Types.newParameterizedType(FutureEntry, String, type)) {}
        this.publisher = publisher
        publisher.subscribe(this)
    }

    @Override
    abstract String group()

    /**
     * Create a new {@link CompletableFuture} instance. The future can be "completed" by this or any other instance
     * having the same {@link #group()} value.
     *
     * @param key A key identifying this future in an univocal manner.
     * @return A {@link CompletableFuture} object.
     */
    @Override
    CompletableFuture<V> create(String key) {
        final result = new CompletableFuture<V>()
                .orTimeout(ttl.toMillis(), TimeUnit.MILLISECONDS)

        final exists = allFutures.putIfAbsent(key, result)!=null
        if( exists )
            throw new IllegalArgumentException("Key already existing: '$key'")
        return result
    }

    /**
     * Receive a message and fulfill the the corresponding {@link CompletableFuture}
     * if available in this instance.
     *
     * @param message A string message representing a JSON serialised {@link FutureEntry} that holds
     * the object value and its unique key.
     */
    @Override
    void receive(String message) {
        final FutureEntry<String,V> entry = encodingStrategy.decode(message)
        final result = allFutures.get(entry.key)
        if( result != null ) {
            result.complete(entry.value)
            allFutures.remove(entry.key)
        }
        else {
            log.debug "Missing future entry with key: '$entry.key'"
        }
    }

    /**
     * Notify the completion of a {@link CompletableFuture}. This method can be invoked by any
     * of the parties sharing the same {@link #group()} name, irrespective where the future object
     * was created with the {@link #create(java.lang.String)}  method.
     *
     * @param key The {@link CompletableFuture} unique key.
     * @param value The value objected to be assigned to the {@link CompletableFuture} identified by the {@code key} parameter.
     */
    @Override
    void complete(String key, V value) {
        final encoded = encodingStrategy.encode(new FutureEntry<String,V>(key,value))
        publisher.publish(encoded)
    }

    /**
     * Cancel all non-completed futures nad close related resources.
     */
    @PreDestroy
    @Override
    void close() {
        // cancel all pending futures
        for( CompletableFuture<V> fut : allFutures.values() ) {
            fut.cancel(true)
        }
        // close the corresponding publisher
        publisher.close()
    }
}
