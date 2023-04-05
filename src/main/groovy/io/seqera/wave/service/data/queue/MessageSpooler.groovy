package io.seqera.wave.service.data.queue

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j
import io.seqera.wave.util.ExponentialAttempt

/**
 * Implements a queue spooler that takes care removing the message from a queue
 * as they are made available and dispatch them to the corresponding consumer
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class MessageSpooler implements Runnable {

    static final private AtomicInteger count = new AtomicInteger()

    @EqualsAndHashCode
    @CompileStatic
    static class QueueConsumer<T> implements Consumer<T> {

        final String id

        @Delegate
        private Consumer<T> delegate

        QueueConsumer(String id, Consumer<T> consumer) {
            this.id = id
            this.delegate = consumer
        }

    }

    final String key
    final List<QueueConsumer<String>> consumers
    final MessageBroker<String> broker
    private Thread thread
    private final Random random = new Random()
    private final ExponentialAttempt attempt = new ExponentialAttempt()

    MessageSpooler(String key, MessageBroker<String> broker) {
        log.debug "Creating spooler thread for queue: $key"
        this.key = key
        this.broker = broker
        this.consumers = new ArrayList<>(10)
        this.thread = null
    }

    MessageSpooler(String key, MessageBroker<String> broker, boolean startThread) {
        log.debug "Creating spooler thread for queue: $key"
        this.key = key
        this.broker = broker
        this.consumers = new ArrayList<>(10)
        this.thread = new Thread(this, "message-spooler-${count.getAndIncrement()}")
        this.thread.setDaemon(true)
        this.thread.start()
        // init
        broker.init(key)
    }

    @Override
    void run() {
        while( !thread.isInterrupted() ) {
            try {
                final value = broker.poll(key, Duration.ofSeconds(1))
                if( value != null ) {
                    final p = random.nextInt(0,consumers.size())
                    consumers[p].accept(value)
                }
                //
                attempt.reset()
            }
            catch (InterruptedException e) {
                log.debug "Interrupting spooler thread for queue: $key"
                Thread.currentThread().interrupt()
                break
            }
            catch (Throwable e) {
                final d0 = attempt.delay()
                log.error("Unexpected error on message spooler (await: ${d0}) - cause: ${e.message}", e)
                sleep(d0.toMillis())
            }
        }
    }

    void offer(String message) {
        broker.offer(key, message)
    }

    boolean exists(String key) {
        broker.exists(key)
    }

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
            log.debug "Unexpected exception while waiting spooler thread $key - cause: ${e.message}"
        }
        // remove the key
        broker.delete(key)
    }

    MessageSpooler addConsumer(String id, Consumer<String> consumer) {
        consumers.add(new QueueConsumer<String>(id,consumer))
        return this
    }

    int removeConsumer(String consumerId) {
        final p = consumers.findIndexOf( (it)->it.id == consumerId)
        if( p>=0 )
            consumers.remove(p)
        return consumers.size()
    }
}
