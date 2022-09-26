package io.seqera.wave.service.cache
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CacheStore<K,V> {

    V get(K key)

    void put(K key, V value)

    boolean putIfAbsent(K key, V value)

    void remove(K key)

}
