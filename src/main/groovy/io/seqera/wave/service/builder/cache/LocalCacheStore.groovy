package io.seqera.wave.service.builder.cache


import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

import groovy.transform.CompileStatic
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class LocalCacheStore<K,V> implements CacheStore<K,V> {

    private ConcurrentHashMap<K,V> store = new ConcurrentHashMap<>()

    private ConcurrentHashMap<K, CountDownLatch> watchers = new ConcurrentHashMap<>()

    @Override
    boolean containsKey(K key) {
        return store.containsKey(key)
    }

    @Override
    V get(K key) {
        return store.get(key)
    }

    @Override
    V await(K key) {
        final latch = watchers.get(key)
        if( latch ) {
            latch.await()
            return store.get(key)
        }
        else
            return null
    }

    @Override
    void put(K key, V value) {
        store.put(key, value)
        final latch = watchers.putIfAbsent(key, new CountDownLatch(1))
        if( latch!=null )
            latch.countDown()
    }

}
