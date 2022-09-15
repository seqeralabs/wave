package io.seqera.wave.service.builder.cache


import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.builder.BuildRequest
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'redis.uri')
@Singleton
@CompileStatic
class LocalCacheStore implements CacheStore<String , BuildRequest> {

    private ConcurrentHashMap<String, BuildRequest> store = new ConcurrentHashMap<>()

    private ConcurrentHashMap<String, CountDownLatch> watchers = new ConcurrentHashMap<>()

    @Override
    boolean containsKey(String key) {
        return store.containsKey(key)
    }

    @Override
    BuildRequest get(String key) {
        return store.get(key)
    }

    @Override
    BuildRequest await(String key) {
        final latch = watchers.get(key)
        if( latch ) {
            latch.await()
            return store.get(key)
        }
        else
            return null
    }

    @Override
    void put(String key, BuildRequest value) {
        store.put(key, value)
        final latch = watchers.putIfAbsent(key, new CountDownLatch(1))
        if( latch!=null )
            latch.countDown()
    }

}
