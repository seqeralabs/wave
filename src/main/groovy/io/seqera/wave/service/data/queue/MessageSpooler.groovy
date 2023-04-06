package io.seqera.wave.service.data.queue

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j
import io.micronaut.websocket.exceptions.WebSocketSessionException
import io.seqera.wave.exception.NoSenderAvailException
import io.seqera.wave.service.pairing.socket.MessageSender
import io.seqera.wave.util.ExponentialAttempt
/**
 * Implements a queue spooler that takes care removing the message from a queue
 * as they are made available and dispatch them to the corresponding sender
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class MessageSpooler implements Runnable {

    static final private AtomicInteger count = new AtomicInteger()

    @EqualsAndHashCode
    @CompileStatic
    static class Sender<T> implements MessageSender<T> {

        final String id

        @Delegate
        private MessageSender<T> delegate

        Sender(String id, MessageSender<T> sender) {
            this.id = id
            this.delegate = sender
        }

    }

    final String target
    final List<Sender<String>> senders
    final MessageBroker<String> broker
    private Thread thread
    private final Random random = new Random()
    private final ExponentialAttempt attempt = new ExponentialAttempt()

    MessageSpooler(String target, MessageBroker<String> broker) {
        log.debug "Creating spooler thread for target '$target'"
        this.target = target
        this.broker = broker
        this.senders = new ArrayList<>(10)
        this.thread = null
    }

    MessageSpooler(String target, MessageBroker<String> broker, boolean startThread) {
        log.debug "Creating spooler thread for target '$target'"
        this.target = target
        this.broker = broker
        this.senders = new ArrayList<>(10)
        this.thread = new Thread(this, "message-spooler-${count.getAndIncrement()}")
        this.thread.setDaemon(true)
        this.thread.start()
        // init
        broker.init(target)
    }

    private void send(String message) {
        // make a copy to prevent concurrent modifications
        // made by 'addClient' and 'removeClient' methods
        final avail = new ArrayList<Sender<String>>(senders)
        while( avail.size()>0 ) {
            final p = random.nextInt(0,avail.size())
            final sender = avail[p]
            try {
                sender.sendAsync(message)
                return
            }
            catch (WebSocketSessionException e) {
                // this session is not available any more, remove it from the list of avail senders
                log.debug "Websocket session with id '${sender.id}' has been closed"
                avail.remove(p)
            }
        }
        if( !avail ) {
            // throw an exception to cause a delay before the next attempt
            throw new NoSenderAvailException("No sender available for Websocket target '$target'")
        }
    }

    @Override
    void run() {
        while( !thread.isInterrupted() ) {
            try {
                final value = broker.poll(target, Duration.ofSeconds(1))
                if( value != null ) {
                    send(value)
                }
                //
                attempt.reset()
            }
            catch (InterruptedException e) {
                log.debug "Interrupting spooler thread for queue: $target"
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
        broker.offer(target, message)
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
            log.debug "Unexpected exception while waiting spooler thread $target - cause: ${e.message}"
        }
        // remove the key
        broker.delete(target)
    }

    MessageSpooler addClient(String clientId, MessageSender<String> sender) {
        senders.add(new Sender<String>(clientId, sender))
        return this
    }

    int removeClient(String clientId) {
        final p = senders.findIndexOf( (it)->it.id == clientId)
        if( p>=0 ) {
            senders.remove(p)
        }
        else {
            log.warn "Unable find a sender with clientId: '$clientId'"
        }
        return senders.size()
    }
}
