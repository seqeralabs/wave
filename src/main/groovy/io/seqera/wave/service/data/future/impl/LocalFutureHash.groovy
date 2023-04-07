package io.seqera.wave.service.data.future.impl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.future.FutureHash
import jakarta.inject.Singleton
/**
 * Implement a future queue based on a simple hash map.
 * This is only meant for local/development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Singleton
@CompileStatic
class LocalFutureHash implements FutureHash<String> {

    private ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>()

    @Override
    void put(String key, String value, Duration expiration) {
        store.putIfAbsent(key, value)
    }

    @Override
    String take(String key) {
        return store.remove(key)
    }
}
