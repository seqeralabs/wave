package io.seqera.wave.service.data.queue.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
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
@Singleton
@CompileStatic
class LocalQueueBroker implements MessageBroker<String> {

    private ConcurrentHashMap<String, LinkedBlockingQueue<String>> store = new ConcurrentHashMap<>()

    private ConcurrentHashMap<String, Boolean> marks = new ConcurrentHashMap<>()

    @Override
    void offer(String target, String message) {
        store
            .computeIfAbsent(target, (it)->new LinkedBlockingQueue<String>())
            .offer(message)
    }

    @Override
    String poll(String target) {
        store
            .computeIfAbsent(target, (it)->new LinkedBlockingQueue<String>())
            .poll()
    }

    @Override
    void mark(String key) {
        marks.put(key, true)
    }

    @Override
    void unmark(String key) {
        marks.remove(key)
    }

    @Override
    boolean matches(String key) {
        marks.keySet().find((String it) -> it.startsWith(key))
    }
}
