package io.seqera.wave.service.data.future

import java.time.Duration
/**
 * Define the interface for a future distributed hash
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureHash<V> {

    /**
     * Add a value in the distributed "queue". The value is evicted after
     * the specified expired duration
     *
     * @param key The key associated with the provided value
     * @param value The value to be stored.
     * @param expiration The amount of time after which the value is evicted
     */
    void put(String key, V value, Duration expiration)

    /**
     * Get the value with the specified key
     *
     * @param key The key of the value to be taken
     * @return The value associated with the specified key or {@code null} otherwise
     */
    V take(String key)

}
