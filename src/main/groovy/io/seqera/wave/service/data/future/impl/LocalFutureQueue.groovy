package io.seqera.wave.service.data.future.impl

import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.future.FutureQueue
import jakarta.inject.Singleton
/**
 * Implement a future queue based on a simple blocking queue.
 * This is only meant for local/development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Singleton
@CompileStatic
class LocalFutureQueue implements FutureQueue<String> {

    private ConcurrentHashMap<String, BlockingQueue<String>> store = new ConcurrentHashMap<>()

    @Override
    void offer(String key, String value) {
        store.computeIfAbsent(key, (it)-> new LinkedTransferQueue<String>())
        store.get(key).offer(value)
    }

    @Override
    String poll(String key, Duration timeout) throws TimeoutException {
        store.computeIfAbsent(key, (it)-> new LinkedTransferQueue<String>())
        final result = store.get(key).poll(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if( result==null )
            throw new TimeoutException("Unable to retrieve a value for key: $key")
        store.remove(key)
        return result
    }
}
