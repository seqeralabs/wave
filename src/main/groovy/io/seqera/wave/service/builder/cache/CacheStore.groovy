package io.seqera.wave.service.builder.cache
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CacheStore<K,V> {

    boolean containsKey(K key)

    V get(K key)

    void put(K key, V value)

    V await(K key)

}
