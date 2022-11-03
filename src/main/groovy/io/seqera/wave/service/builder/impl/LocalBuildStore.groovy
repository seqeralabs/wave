package io.seqera.wave.service.builder.impl

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.model.BuildResult
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

    private static class Entry<V> {
        final V value
        final Duration ttl
        final Instant ts
        Entry(V value, Duration ttl) {
            this.value = value
            this.ttl = ttl
            this.ts = Instant.now()
        }

        boolean isExpired() {
            ts.plus(ttl) <= Instant.now()
        }
    }

    private ConcurrentHashMap<String, Entry<BuildResult>> store = new ConcurrentHashMap<>()

    @Value('${wave.build.status.duration:`1d`}')
    private Duration duration

    @Value('${wave.build.status.delay:5s}')
    private Duration delay

    @Value('${wave.build.timeout:5m}')
    private Duration timeout

    Duration getDelay() { delay }

    Duration getTimeout() { timeout }

    @Override
    BuildResult getBuild(String imageName) {
        final entry = store.get(imageName)
        if( !entry ) {
            return null
        }
        if( entry.isExpired() ) {
            store.remove(imageName)
            return null
        }
        return entry.value
    }

    @Override
    void storeBuild(String imageName, BuildResult request) {
        store.put(imageName, new Entry<>(request,duration))
    }

    @Override
    void storeBuild(String imageName, BuildResult request, Duration ttl) {
        store.put(imageName, new Entry<>(request,ttl))
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildResult build) {
        final entry = store.get(imageName)
        if( entry?.isExpired() )
            store.remove(imageName)
        return store.putIfAbsent(imageName, new Entry<>(build,duration))==null
    }

    @Override
    void removeBuild(String imageName) {
        store.remove(imageName)
    }
}
