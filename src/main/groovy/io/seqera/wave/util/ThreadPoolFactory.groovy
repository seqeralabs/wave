package io.seqera.wave.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Factory class to create a singleton thread pool instance
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
class ThreadPoolFactory {

    @Singleton
    @Named('fixedThreadPool')
    ExecutorService fixedThreadPool(@Value('${wave.io.fixedPool.maxThreads:32}') int maxThreads) {
        return Executors.newFixedThreadPool(maxThreads, new CustomThreadFactory('wave-io-fixed-pool'))
    }

}
