package io.seqera.wave.service.data.future

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.util.ExponentialAttempt

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
abstract class AbstractFutureStore<V> implements FutureStore<String,V>, AutoCloseable, Runnable {

    private static final AtomicInteger count = new AtomicInteger()

    private EncodingStrategy<V> encodingStrategy

    private FutureQueue<String> queue

    private ConcurrentHashMap<String,CompletableFuture> futures = new ConcurrentHashMap<>()

    private Thread thread

    private String name0

    private ExponentialAttempt attempt

    AbstractFutureStore(FutureQueue<String> queue, EncodingStrategy<V> encodingStrategy) {
        this.encodingStrategy = encodingStrategy
        this.queue = queue
        this.name0 = "${name()}-${count.getAndIncrement()}"
        this.attempt = new ExponentialAttempt()
    }

    protected void start() {
        this.thread = new Thread(this, name0)
        this.thread.setDaemon(true)
        thread.start()
    }

    abstract String topic()

    abstract Duration timeout()

    abstract name()

    abstract Duration pollInterval()

    /**
     * Create a new {@link CompletableFuture} instance. The future can be "completed" by this or any other instance
     * having the same {@link #topic()} value.
     *
     * @param key A key identifying this future in an univocal manner.
     * @return A {@link CompletableFuture} object.
     */
    @Override
    CompletableFuture<V> create(String key) {
        final result = new CompletableFuture().orTimeout(timeout().toMillis(), TimeUnit.MILLISECONDS)
        futures.put(key, result)
        return result
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
        final expiration = Duration.ofMillis( timeout().toMillis() *10 )
        queue.offer(target, encoded, expiration)
    }

    /**
     * Cancel all non-completed futures nad close related resources.
     */
    @Override
    void close() {
        if( !thread )
            return
        // interrupt the thread
        thread.interrupt()
        // wait for the termination
        try {
            thread.join(1_000)
        }
        catch (Exception e) {
            log.debug "Unexpected exception while waiting thread ${name0} - cause: ${e.message}"
        }
        // close the queue
        queue.close()
    }

    @Override
    void run() {

        while( !thread.isInterrupted() ) {
            try {
                final itr = futures.keySet().iterator()
                while( itr.hasNext() ) {
                    final key = itr.next()
                    final target = topic() + key
                    final result = queue.poll(target)
                    if( result != null ) {
                        log.trace "Completing future $result"
                        final fut = futures.remove(key)
                        fut.complete( encodingStrategy.decode(result) )
                    }
                }
                // sleep for a while
                sleep(pollInterval().toMillis())
                attempt.reset()
            }
            catch (InterruptedException e) {
                log.debug "Interrupting exception on ${name0} -- cause: ${e.message}"
                Thread.currentThread().interrupt()
                break
            }
            catch(Throwable e) {
                final d0 = attempt.delay()
                log.error "Unexpected error on ${name0} (await: $d0) -- cause: ${e.message}", e
                sleep(d0.toMillis())
            }
        }
    }
}
