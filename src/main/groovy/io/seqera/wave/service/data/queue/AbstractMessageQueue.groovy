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

package io.seqera.wave.service.data.queue

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.websocket.exceptions.WebSocketSessionException
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.pairing.socket.MessageSender
import io.seqera.wave.util.ExponentialAttempt
import io.seqera.wave.util.TypeHelper
/**
 * Implements a distributed message queue in which many listeners can register
 * to consume a message. A message instance can be consumed by one and only listener.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 *
 * @param <M>    The type of message that can be sent through the broker.
 */
@Slf4j
@CompileStatic
abstract class AbstractMessageQueue<M> implements Runnable {

    final private static AtomicInteger count = new AtomicInteger()

    final private MessageBroker<String> broker

    final private EncodingStrategy<M> encoder

    final private ExponentialAttempt attempt = new ExponentialAttempt()

    final private Thread thread

    final private ConcurrentHashMap<String,MessageSender<String>> clients = new ConcurrentHashMap<>()

    final private String name0

    final private Cache<String,Boolean> closedClients = CacheBuilder<String, Boolean>
                    .newBuilder()
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build()

    AbstractMessageQueue(MessageBroker<String> broker) {
        final type = TypeHelper.getGenericType(this, 0)
        this.encoder = new MoshiEncodeStrategy<M>(type) {}
        this.broker = broker
        this.name0 = name() + '-thread-' + count.getAndIncrement()
        this.thread = new Thread(this, name0)
        this.thread.setDaemon(true)
        this.thread.start()
    }

    protected abstract String name()

    protected abstract Duration pollInterval()

    protected abstract String prefix()

    protected String targetKey(String k) {
        return prefix() + k
    }

    protected String clientKey(String target, String clientId) {
        return targetKey(target) + ":client=$clientId/queue"
    }

    protected String targetFromClientKey(String clientKey) {
        final p = clientKey.indexOf(':client=')
        if( p==-1 ) throw new IllegalArgumentException("Invalid client key: '$clientKey'")
        return clientKey.substring(0,p)
    }

    /**
     * Add a message to the send queue
     *
     * @param target The name of the queue to which the message should be added
     * @param message The message to queue
     */
    void offer(String target, M message) {
        // serialise the message to a string
        final serialized = encoder.encode(message)
        // add the message the target queue
        // NOTE: all clients connecting from the same endpoint
        // will use the *same* target queue name
        // Any register sender having matching target key
        // will be able to take and send the message
        broker.offer(targetKey(target), serialized)
    }

    /**
     * Register a websocket client to which a {@link MessageSender} instance
     * can send a message
     *
     * @param target
     *      Identify the websocket target i.e. the remote endpoint. For the same target
     *      it's possible to have one ore more clients
     * @param clientId
     *      A unique id for the Websocket client instance
     * @param sender
     *      A {@link MessageSender} that will send to message to the client 
     */
    void registerClient(String target, String clientId, MessageSender<M> sender) {
        clients.put(clientKey(target,clientId), new MessageSender<String>() {
            @Override
            void send(String message) {
                final decodeMessage = encoder.decode(message)
                sender.send(decodeMessage)
            }
        })
    }

    /**
     * Unregister a websocket client
     *
     * @param target
     *      Identify the websocket target i.e. the remote endpoint. For the same target
     *      it's possible to have one ore more clients
     * @param clientId
     *      A unique id for the Websocket client instance
     */
    void unregisterClient(String target, String clientId) {
        clients.remove(clientKey(target,clientId))
    }

    @Override
    void run() {
        while( !thread.isInterrupted() ) {
            try {
                int sent=0
                final clients = new HashMap<String,MessageSender<String>>(this.clients)
                for( Map.Entry<String,MessageSender<String>> entry : clients ) {
                    // ignore clients marked as closed
                    if( closedClients.getIfPresent(entry.key))
                        continue
                    // infer the target queue from the client key
                    final target = targetFromClientKey(entry.key)
                    // poll for a message from the queue
                    final value = broker.take(target)
                    // if there's a message try to send it
                    if( value != null ) {
                        try {
                            entry.value.send(value)
                            sent++
                        }
                        catch (WebSocketSessionException e) {
                            log.warn "Unable to send message ${value} - cause: ${e.message}"
                            // it could not manage to send the event
                            // offer back the value to be processed again
                            broker.offer(target, value)
                            if( e.message?.contains('close') ) {
                                closedClients.put(entry.key, true)
                            }
                        }
                    }
                }
                // reset the attempt count because no error has been thrown
                attempt.reset()
                // if no message was sent, sleep for a while before retrying
                if( sent==0 ) {
                    sleep(pollInterval().toMillis())
                }
            }
            catch (InterruptedException e) {
                log.debug "Interrupting spooler thread for queue ${name0}"
                Thread.currentThread().interrupt()
                break
            }
            catch (Throwable e) {
                final d0 = attempt.delay()
                log.error("Unexpected error on queue ${name0} (await: ${d0}) - cause: ${e.message}", e)
                sleep(d0.toMillis())
            }
        }
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
            log.debug "Unexpected error while terminating ${name0} - cause: ${e.message}"
        }
    }
}
