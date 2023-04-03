package io.seqera.wave.service.data.queue.impl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.queue.MessageBroker
import jakarta.inject.Singleton
/**
 * Implement a message broker based on a simple blocking queue.
 * This is only meant for local/development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Primary
@Singleton
@CompileStatic
class LocalQueueBroker implements MessageBroker<String> {

    private ConcurrentHashMap<String, LinkedBlockingQueue<String>> store = new ConcurrentHashMap<>()

    @Override
    void offer(String key, String message) {
        store
            .computeIfAbsent(key, (it)-> new LinkedBlockingQueue<>())
            .offer(message)
    }

    @Override
    String poll(String key, Duration timeout) {
        store
            .computeIfAbsent(key, (it)-> new LinkedBlockingQueue<>())
            .poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
    }

    @Override
    void delete(String key) {
        store.remove(key)
    }

    void init(String key) {
        store
                .computeIfAbsent(key, (it)-> new LinkedBlockingQueue<>())
    }

    boolean exists(String key) {
        store.containsKey(key)
    }
}
