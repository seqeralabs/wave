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

    protected abstract String name()

    protected abstract Duration pollInterval()

    void offer(String streamId, M message) {
        final msg = encoder.encode(message)
        stream.offer(streamId, msg)
    }

    void consume(String streamId, Predicate<M> consumer) {
        listeners.put(streamId, consumer)
    }

    protected void processMessages() {
        log.trace "Message stream - starting listener thread"
        while( !thread.interrupted() ) {
            try {
                int count=0
                for( Map.Entry<String,Predicate<M>> entry : listeners.entrySet() ) {
                    final consumer0 = entry.value
                    stream.consume(entry.key, (String msg)-> {
                        count+=1
                        log.trace "Message streaming - receiving message=$msg"
                        consumer0.test(encoder.decode(msg))
                    })
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
