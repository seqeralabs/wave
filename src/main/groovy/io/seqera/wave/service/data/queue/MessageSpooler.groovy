package io.seqera.wave.service.data.queue

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

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

    final String key
    final Consumer<String> consumer
    final MessageBroker<String> broker
    final Thread thread

    MessageSpooler(String key, MessageBroker<String> broker) {
        log.debug "Creating spooler thread for queue: $key"
        this.key = key
        this.broker = broker
        this.consumer = null
        this.thread = null
    }

    MessageSpooler(String key, MessageBroker<String> broker, Consumer<String> consumer) {
        log.debug "Creating spooler thread for queue: $key"
        this.key = key
        this.broker = broker
        this.consumer = consumer
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
                    consumer.accept(value)
                }
            }
            catch (InterruptedException e) {
                log.debug "Interrupting spooler thread for queue: $key"
                Thread.currentThread().interrupt()
                break
            }
            catch (Throwable e) {
                log.error("Unexpected error on message spooler - cause: ${e.message}", e)
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
}
