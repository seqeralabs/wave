package io.seqera.wave.service.builder.impl

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStore
import jakarta.inject.Singleton
/**
 * Implement local version of {@link BuildStore}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalBuildStore implements BuildStore {

    private ConcurrentHashMap<String, BuildResult> store = new ConcurrentHashMap<>()

    @Value('${wave.build.status.delay:5s}')
    private Duration delay

    @Value('${wave.build.timeout:5m}')
    private Duration timeout

    Duration getDelay() { delay }

    Duration getTimeout() { timeout }

    @Override
    BuildResult getBuild(String imageName) {
        return store.get(imageName)
    }

    @Override
    void storeBuild(String imageName, BuildResult request) {
        store.put(imageName, request)
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildResult build) {
        return store.putIfAbsent(imageName, build)==null
    }

    @Override
    void removeBuild(String imageName) {
        store.remove(imageName)
    }
}
