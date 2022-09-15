package io.seqera.wave.service.builder.impl

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStore
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalCacheStore implements BuildStore {

    private ConcurrentHashMap<String, BuildResult> store = new ConcurrentHashMap<>()

    private ConcurrentHashMap<String, CountDownLatch> watchers = new ConcurrentHashMap<>()

    @Override
    boolean hasBuild(String imageName) {
        return store.containsKey(imageName)
    }

    @Override
    BuildResult getBuild(String imageName) {
        return store.get(imageName)
    }

    @Override
    CompletableFuture<BuildResult> awaitBuild(String imageName) {
        final latch = watchers.get(imageName)
        if( !latch ) {
             return null
        }

        CompletableFuture<BuildResult>.supplyAsync(() -> {
            latch.await()
            return store.get(imageName)
        })
    }

    @Override
    void storeBuild(String imageName, BuildResult request) {
        store.put(imageName, request)
        final latch = watchers.putIfAbsent(imageName, new CountDownLatch(1))
        if( latch!=null )
            latch.countDown()
    }

}
