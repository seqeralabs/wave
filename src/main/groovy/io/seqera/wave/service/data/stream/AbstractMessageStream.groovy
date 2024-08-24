/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.data.stream

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.util.ExponentialAttempt
import io.seqera.wave.util.TypeHelper
/**
 * Implement an abstract stream that allows that consumes messages asynchronously
 * as soon as they are available.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
abstract class AbstractMessageStream<M> implements Closeable {

    static final private Map<String,Predicate<M>> listeners = new ConcurrentHashMap<>()

    static final private AtomicInteger count = new AtomicInteger()

    final private ExponentialAttempt attempt = new ExponentialAttempt()

    final private EncodingStrategy<M> encoder

    final private MessageStream<String> stream

    private Thread thread

    private String name0

    AbstractMessageStream(MessageStream<String> target) {
        final type = TypeHelper.getGenericType(this, 0)
        this.encoder = new MoshiEncodeStrategy<M>(type) {}
        this.stream = target
        this.name0 = name() + '-thread-' + count.getAndIncrement()
        this.thread = new Thread(()-> processMessages(), name0)
        this.thread.setDaemon(true)
        this.thread.start()
    }

    /**
     * @return The name of the message queue implementation
     */
    protected abstract String name()

    /**
     * @return
     *      The time interval to await before trying to read again the stream
     *      when no more entries are available.
     */
    protected abstract Duration pollInterval()

    /**
     * Add a message to the stream with the specified identifier
     *
     * @param streamId
     *      The stream unique ID
     * @param message
     *      The message object to be added to the stream
     */
    void offer(String streamId, M message) {
        final msg = encoder.encode(message)
        stream.offer(streamId, msg)
    }

    /**
     * Define the consumer {@link Predicate} to be applied when a message is available
     * for reading in the stream.
     *
     * @param streamId
     *      The stream unique ID
     * @param consumer
     *      The {@link Predicate} to be invoked when a stream message is consumed (read from) the stream.
     */
    void consume(String streamId, Predicate<M> consumer) {
        final value = listeners.put(streamId, consumer)
        if( value!=null )
            throw new IllegalStateException("Only one consumer can be defined for each stream - offending streamId=$streamId; consumer=$consumer")
    }

    /**
     * Deserialize the message as string into the target message object and process it by applying
     * the given consumer {@link Predicate<M>}.
     *
     * @param msg
     *      The message serialised as a string value
     * @param consumer
     *      The consumer {@link Predicate<M>} that will handle the message as a object
     * @param count
     *      An {@link AtomicInteger} counter incremented by one when this method is invoked,
     *      irrespective if the consumer is successful or not.
     * @return
     *      The result of the consumer {@link Predicate<M>} operation.
     */
    protected boolean processMessage(String msg, Predicate<M> consumer, AtomicInteger count) {
        count.incrementAndGet()
        final decoded = encoder.decode(msg)
        log.trace "Message streaming - receiving message=$msg; decoded=$decoded"
        return consumer.test(decoded)
    }

    /**
     * Process the messages as they are available from the underlying stream
     */
    protected void processMessages() {
        log.trace "Message stream - starting listener thread"
        while( !thread.interrupted() ) {
            try {
                final count=new AtomicInteger()
                for( Map.Entry<String,Predicate<M>> entry : listeners.entrySet() ) {
                    final streamId = entry.key
                    final consumer = entry.value
                    stream.consume(streamId, (String msg)-> processMessage(msg, consumer, count))
                }
                // reset the attempt count because no error has been thrown
                attempt.reset()
                // if no message was sent, sleep for a while before retrying
                if( count==0 ) {
                    sleep(pollInterval().toMillis())
                }
            }
            catch (InterruptedException e) {
                log.debug "Message streaming interrupt exception - cause: ${e.message}"
                Thread.currentThread().interrupt()
                break
            }
            catch (Throwable e) {
                final d0 = attempt.delay()
                log.error("Unexpected error on message stream ${name0} (await: ${d0}) - cause: ${e.message}", e)
                sleep(d0.toMillis())
            }
        }
        log.trace "Message stream - exiting listener thread"
    }

    /**
     * Shutdown orderly the stream
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
            log.debug "Unexpected error while terminating ${name0} - cause: ${e.message}"
        }
    }
}
